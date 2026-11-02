package com.talkcare.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.talkcare.model.Conversation;
import com.talkcare.model.Message;
import com.talkcare.model.ServiceType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Client conversations with professionals, and the professionals' inbox. */
public final class ChatApi {
    private static final TypeReference<List<Message>> MESSAGES = new TypeReference<>() {};
    private static final TypeReference<List<Conversation>> CONVERSATIONS = new TypeReference<>() {};

    private final ApiClient client;

    ChatApi(ApiClient client) {
        this.client = client;
    }

    /** Opens (or reuses) the client's conversation for a service. */
    public CompletableFuture<Conversation> openConversation(ServiceType service) {
        return client.post("/api/conversations", Map.of("serviceType", service.name()), Conversation.class);
    }

    public CompletableFuture<List<Message>> messages(long conversationId) {
        return client.get("/api/conversations/" + conversationId + "/messages", MESSAGES);
    }

    /** Sends a client message; the answer is the updated message list (including the auto-reply). */
    public CompletableFuture<List<Message>> send(long conversationId, String text, String attachmentDataUrl) {
        return client.post("/api/conversations/" + conversationId + "/messages",
                Map.of("messageText", text, "attachmentData", attachmentDataUrl == null ? "" : attachmentDataUrl),
                MESSAGES);
    }

    public CompletableFuture<List<Conversation>> assigned() {
        return client.get("/api/conversations/assigned", CONVERSATIONS);
    }

    public CompletableFuture<Void> reply(long conversationId, String text) {
        return client.post("/api/conversations/" + conversationId + "/replies", Map.of("messageText", text));
    }
}
