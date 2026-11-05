package com.talkcare.api;

import com.talkcare.model.ChatbotConversation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The TalkCare Chatbot. Gemini, its key and the guardrails all live on the API; the desktop only
 * sends the client's message and shows the conversation the server returns.
 */
public final class ChatbotApi {
    private final ApiClient client;

    ChatbotApi(ApiClient client) {
        this.client = client;
    }

    public CompletableFuture<ChatbotConversation> conversation() {
        return client.get("/api/chatbot/messages", ChatbotConversation.class);
    }

    public CompletableFuture<ChatbotConversation> send(String message) {
        return client.post("/api/chatbot/messages", Map.of("message", message), ChatbotConversation.class);
    }

    public CompletableFuture<ChatbotConversation> reset() {
        return client.post("/api/chatbot/reset", Map.of(), ChatbotConversation.class);
    }
}
