package com.talkcare.api;

import com.talkcare.model.Account;
import com.talkcare.model.ServerMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Sign-in, registration, sign-out and password reset. */
public final class AuthApi {
    private final ApiClient client;

    AuthApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<Account> login(String username, String password) {
        return client.post("/api/auth/login", Map.of("username", username, "password", password), Account.class);
    }

    public CompletableFuture<Account> register(Registration registration) {
        return client.post("/api/auth/register", registration, Account.class);
    }

    public CompletableFuture<Void> logout() {
        return client.post("/api/auth/logout", null);
    }

    public CompletableFuture<ServerMessage> requestPasswordReset(String email) {
        return client.post("/api/auth/forgot-password", Map.of("email", email), ServerMessage.class);
    }

    public CompletableFuture<ServerMessage> resetPassword(String token, String newPassword) {
        return client.post("/api/auth/reset-password", Map.of("token", token, "newPassword", newPassword),
                ServerMessage.class);
    }

    /** Body of POST /api/auth/register. */
    public record Registration(String fullName, String username, String address, String contactNo,
                               String email, String password) {}
}
