package com.talkcare.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * HTTP + JSON access to the TalkCare REST API. All calls are asynchronous; callers switch back
 * to the JavaFX thread before touching the page (see {@code com.talkcare.ui.Ui}).
 */
public final class ApiClient {
    /** Render's free plan sleeps when idle; the first request can take close to a minute. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;
    private volatile String sessionToken;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void setSessionToken(String token) {
        this.sessionToken = token;
    }

    /** Uploaded files are served by the API; bundled assets stay relative to the local UI. */
    public String resolveAssetUrl(String url) {
        if (url == null || url.isBlank()) return "";
        return url.startsWith("/uploads/") ? baseUrl + url : url;
    }

    public static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public <T> CompletableFuture<T> get(String path, Class<T> type) {
        return send(request(path).GET(), body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> get(String path, TypeReference<T> type) {
        return send(request(path).GET(), body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> post(String path, Object payload, Class<T> type) {
        return send(withBody(request(path), "POST", payload), body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> post(String path, Object payload, TypeReference<T> type) {
        return send(withBody(request(path), "POST", payload), body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> patch(String path, Object payload, Class<T> type) {
        return send(withBody(request(path), "PATCH", payload), body -> json.readValue(body, type));
    }

    /** POST whose answer is not needed (e.g. logout). */
    public CompletableFuture<Void> post(String path, Object payload) {
        return send(withBody(request(path), "POST", payload == null ? Map.of() : payload), body -> null);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        String token = sessionToken;
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private HttpRequest.Builder withBody(HttpRequest.Builder builder, String method, Object payload) {
        try {
            String body = json.writeValueAsString(payload);
            return builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not encode the request body", e);
        }
    }

    private <T> CompletableFuture<T> send(HttpRequest.Builder builder, BodyReader<T> reader) {
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        throw new CompletionException(new ApiException(
                                "Could not reach the TalkCare server. Check your internet connection, then try again.",
                                error));
                    }
                    if (response.statusCode() / 100 != 2) {
                        throw new CompletionException(new ApiException(response.statusCode(),
                                errorMessage(response)));
                    }
                    try {
                        return reader.read(response.body());
                    } catch (Exception e) {
                        throw new CompletionException(new ApiException(response.statusCode(),
                                "The server sent an unexpected answer."));
                    }
                });
    }

    /** The API reports errors as { "error": "...", "code": "..." } (health uses "message"). */
    private String errorMessage(HttpResponse<String> response) {
        try {
            JsonNode node = json.readTree(response.body());
            for (String field : new String[] {"error", "message"}) {
                JsonNode value = node == null ? null : node.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText();
            }
        } catch (Exception ignored) {
            // Not JSON (e.g. a proxy error page): fall through to the generic message.
        }
        return "Request failed (" + response.statusCode() + ")";
    }

    @FunctionalInterface
    private interface BodyReader<T> {
        T read(String body) throws Exception;
    }
}
