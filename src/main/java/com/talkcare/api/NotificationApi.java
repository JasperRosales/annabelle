package com.talkcare.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.talkcare.model.Notification;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The signed-in user's in-app notifications. */
public final class NotificationApi {
    private static final TypeReference<List<Notification>> NOTIFICATIONS = new TypeReference<>() {};

    private final ApiClient client;

    NotificationApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<List<Notification>> list() {
        return client.get("/api/notifications", NOTIFICATIONS);
    }

    public CompletableFuture<Void> markAllRead() {
        return client.post("/api/notifications/read", null);
    }
}
