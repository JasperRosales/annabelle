package com.talkcare.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.talkcare.model.Account;
import com.talkcare.model.ActivityEntry;
import com.talkcare.model.AdminMetrics;
import com.talkcare.model.AdminRequest;
import com.talkcare.model.Schedule;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Administrator dashboard: metrics, users, pending requests, activity and schedule. */
public final class AdminApi {
    private static final TypeReference<List<AdminRequest>> REQUESTS = new TypeReference<>() {};
    private static final TypeReference<List<ActivityEntry>> ACTIVITY = new TypeReference<>() {};
    private static final TypeReference<List<Account>> USERS = new TypeReference<>() {};

    private final ApiClient client;

    AdminApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<AdminMetrics> metrics() {
        return client.get("/api/admin/metrics", AdminMetrics.class);
    }

    public CompletableFuture<List<AdminRequest>> pendingRequests() {
        return client.get("/api/admin/pending-requests", REQUESTS);
    }

    public CompletableFuture<Void> act(AdminRequest request, String action, String reason, String remarks) {
        return client.post("/api/admin/pending-requests/action",
                new ReviewAction(request.type(), request.id(), action, reason, remarks));
    }

    public CompletableFuture<List<ActivityEntry>> activity() {
        return client.get("/api/admin/activity", ACTIVITY);
    }

    public CompletableFuture<Schedule> schedule(int year, int month) {
        return client.get("/api/admin/schedule?year=" + year + "&month=" + month, Schedule.class);
    }

    public CompletableFuture<List<Account>> users() {
        return client.get("/api/admin/users", USERS);
    }

    /** Body of POST /api/admin/pending-requests/action. */
    public record ReviewAction(String type, long id, String action, String reason, String remarks) {}
}
