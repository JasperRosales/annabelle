package com.talkcare.api;

import com.talkcare.model.Health;

import java.util.concurrent.CompletableFuture;

/** Whether the API, and the database behind it, can be reached. */
public final class HealthApi {
    private final ApiClient client;

    HealthApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<Health> check() {
        return client.get("/api/health", Health.class);
    }
}
