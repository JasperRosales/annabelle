package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One chat message. senderRole is CLIENT, PROFESSIONAL, ADMIN or SYSTEM (auto-reply). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(long id, String senderRole, String messageText, String attachmentUrl, String createdAt) {

    public boolean isSystem() { return "SYSTEM".equals(senderRole); }
    public boolean isFromClient() { return "CLIENT".equals(senderRole); }
    public boolean isFromProfessional() { return "PROFESSIONAL".equals(senderRole); }
    public boolean hasText() { return messageText != null && !messageText.isBlank(); }
    public boolean hasAttachment() { return attachmentUrl != null && !attachmentUrl.isBlank(); }
}
