package com.talkcare.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Serves the bundled HTML/CSS/images to the embedded WebView on localhost only. The page itself
 * is assembled by {@link PageAssembler}; all behaviour is Java ({@code com.talkcare.ui}).
 * <p>
 * Password-reset emails link to {@code http://localhost:<port>/?resetToken=...}. That link opens
 * in the system browser, so the token is handed to the running app instead (see
 * {@link #onResetToken}).
 */
public final class StaticUiServer {
    private static final String ROOT = "static/";
    private static final Map<String, String> MIME_TYPES = Map.of(
            "html", "text/html; charset=UTF-8",
            "css", "text/css; charset=UTF-8",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "svg", "image/svg+xml",
            "json", "application/json; charset=UTF-8");

    private final int port;
    private final PageAssembler pages = new PageAssembler(ROOT);
    private volatile Consumer<String> resetTokenHandler = token -> {};
    private HttpServer server;

    public StaticUiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public String indexUrl() {
        return "http://localhost:" + port + "/index.html";
    }

    /** Called (on a server thread) with the token when a password-reset link is opened. */
    public void onResetToken(Consumer<String> handler) {
        this.resetTokenHandler = handler;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }
        String resetToken = queryParam(exchange.getRequestURI().getRawQuery(), "resetToken");
        if (resetToken != null && !resetToken.isBlank()) {
            resetTokenHandler.accept(resetToken);
            send(exchange, 200, "text/html; charset=UTF-8", RESET_HANDOFF_PAGE);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.contains("..")) {
            send(exchange, 400, "text/plain; charset=UTF-8", "Bad path");
            return;
        }
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (path.equals("/") || path.equals("/index.html") || !file.contains(".")) {
            send(exchange, 200, MIME_TYPES.get("html"), pages.page());
            return;
        }

        byte[] content = readResource(ROOT + path.substring(1));
        if (content == null) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }
        String extension = file.substring(file.lastIndexOf('.') + 1).toLowerCase();
        send(exchange, 200, MIME_TYPES.getOrDefault(extension, "application/octet-stream"), content);
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private byte[] readResource(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            if (!head) os.write(body);
        }
    }

    private static final String RESET_HANDOFF_PAGE = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="UTF-8"><title>TalkCare</title>
            <link rel="stylesheet" href="/css/style.css"></head>
            <body style="display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center;">
              <div><h1>Continue in the TalkCare app</h1>
              <p>Your password reset link was sent to the TalkCare desktop app.<br>
              Switch to the app to choose a new password. You can close this tab.</p></div>
            </body></html>
            """;
}
