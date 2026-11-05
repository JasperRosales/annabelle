package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** The TalkCare Chatbot conversation kept by the API: its greeting and the messages so far (CLIENT or BOT). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotConversation(String greeting, List<Message> messages) {
    public ChatbotConversation {
        greeting = greeting == null ? "" : greeting;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
