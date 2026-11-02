package com.talkcare;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Serves the bundled HTML/CSS/JS UI to the embedded WebView on localhost only.
 * It holds no business logic or database access: the UI talks to the TalkCare API directly.
 * {@code /config.js} tells the UI which API base URL to use.
 */
public final class StaticUiServer {
    private final AppConfig config;
    private HttpServer server;

    public StaticUiServer(AppConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), config.uiPort()), 0);
        server.createContext("/config.js", this::serveConfig);
        server.createContext("/", this::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public String indexUrl() {
        return "http://localhost:" + config.uiPort() + "/index.html";
    }

    private void serveConfig(HttpExchange exchange) throws IOException {
        String js = "window.TALKCARE_CONFIG = Object.freeze({ apiBaseUrl: " + jsString(config.apiBaseUrl()) + " });\n";
        send(exchange, 200, "application/javascript; charset=UTF-8", js.getBytes(StandardCharsets.UTF_8));
    }

    private void serveStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.isEmpty() || path.equals("/")) path = "/index.html";
        if (path.contains("..")) {
            send(exchange, 400, "text/plain; charset=UTF-8", "Bad path".getBytes(StandardCharsets.UTF_8));
            return;
        }

        byte[] content = readResource("static" + path);
        String mime = mimeType(path);
        if (content == null) {
            // Single-page app: unknown routes (e.g. /?resetToken=...) load index.html.
            content = readResource("static/index.html");
            mime = "text/html; charset=UTF-8";
        }
        if (content == null) {
            send(exchange, 404, "text/plain; charset=UTF-8", "TalkCare UI not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(exchange, 200, mime, content);
    }

    private byte[] readResource(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }

    private static String jsString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '<' -> sb.append("\\u003c");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
