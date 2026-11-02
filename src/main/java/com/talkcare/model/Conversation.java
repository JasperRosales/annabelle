package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A client ↔ professional conversation (POST /api/conversations, GET /api/conversations/assigned). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Conversation(long id, long clientId, String clientName, String professionalName, String serviceType) {

    public String clientLabel() {
        return clientName == null || clientName.isBlank() ? "Client #" + clientId : clientName;
    }

    /** Unknown service codes fall back to JusticeCare, as the UI always did. */
    public ServiceType service() {
        return ServiceType.fromCode(serviceType).orElse(ServiceType.JUSTICE_CARE);
    }
}
