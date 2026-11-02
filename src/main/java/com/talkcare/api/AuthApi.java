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

    /** Signs in through a role's own sign-in page; the API refuses accounts with a different role. */
    public CompletableFuture<Account> login(String username, String password, String role) {
        return client.post("/api/auth/login", Map.of("username", username, "password", password, "role", role),
                Account.class);
    }

    public CompletableFuture<Account> register(Registration registration) {
        return client.post("/api/auth/register", registration, Account.class);
    }

    /** Registers an attorney or psychiatrist ({@link Account#ROLE_ATTORNEY} / {@link Account#ROLE_PSYCHIATRIST}). */
    public CompletableFuture<Account> registerProfessional(String role, ProfessionalRegistration registration) {
        String path = switch (role) {
            case Account.ROLE_ATTORNEY -> "/api/auth/register/attorney";
            case Account.ROLE_PSYCHIATRIST -> "/api/auth/register/psychiatrist";
            default -> throw new IllegalArgumentException("Not a professional role: " + role);
        };
        return client.post(path, registration, Account.class);
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

    /**
     * Body of POST /api/auth/register/attorney and /register/psychiatrist. {@code licenseNo} is the
     * Roll of Attorneys or PRC license number; {@code affiliation} the law firm or clinic.
     */
    public record ProfessionalRegistration(String fullName, String username, String address, String contactNo,
                                           String email, String password, String licenseNo, String affiliation) {}
}
