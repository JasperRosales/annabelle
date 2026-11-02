package com.talkcare.api;

import com.talkcare.model.Account;
import com.talkcare.model.UploadedFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The signed-in user's own profile and identity verification. */
public final class ProfileApi {
    private final ApiClient client;

    ProfileApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<Account> update(String username, String email, String password) {
        return client.patch("/api/profile", Map.of("username", username, "email", email, "password", password),
                Account.class);
    }

    /** @param imageDataUrl a data:image/...;base64 URL */
    public CompletableFuture<UploadedFile> uploadPhoto(String imageDataUrl) {
        return client.post("/api/profile/photo", Map.of("image", imageDataUrl), UploadedFile.class);
    }

    public CompletableFuture<Void> submitVerification(Verification verification) {
        return client.post("/api/verifications", verification);
    }

    /** Body of POST /api/verifications. */
    public record Verification(String idType, String idNumber, String address, String email, String idDocImage) {}
}
