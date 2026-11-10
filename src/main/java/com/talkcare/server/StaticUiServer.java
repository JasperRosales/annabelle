package com.talkcare.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serves the bundled HTML/CSS/images to the embedded WebView on localhost only. The page itself
 * is assembled by {@link PageAssembler}; all behaviour is Java ({@code com.talkcare.ui}).
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

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
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
}
