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

    /** Null fields keep their current value; a null password keeps the current password. */
    public CompletableFuture<Account> update(ProfileUpdate update) {
        return client.patch("/api/profile", update, Account.class);
    }

    /** Contact form; works for visitors who are not signed in. */
    public CompletableFuture<Void> contact(String name, String email, String message) {
        return client.post("/api/contact", Map.of("name", name, "email", email, "message", message));
    }

    /** @param imageDataUrl a data:image/...;base64 URL */
    public CompletableFuture<UploadedFile> uploadPhoto(String imageDataUrl) {
        return client.post("/api/profile/photo", Map.of("image", imageDataUrl), UploadedFile.class);
    }

    public CompletableFuture<Void> submitVerification(Verification verification) {
        return client.post("/api/verifications", verification);
    }

    /** Body of PATCH /api/profile. */
    public record ProfileUpdate(String fullName, String username, String email, String contactNo, String address,
                                String password) {}

    /** Body of POST /api/verifications. */
    public record Verification(String idType, String idNumber, String address, String email, String idDocImage) {}
}
