package com.talkcare.models;

import java.sql.Timestamp;

public class ChatMessage {
    private int id;
    private int conversationId;
    private Integer senderId;
    private String senderName;
    private String senderRole;
    private String messageText;
    private boolean autoReply;
    private String attachmentUrl;
    private Timestamp createdAt;

    public ChatMessage() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getConversationId() { return conversationId; }
    public void setConversationId(int conversationId) { this.conversationId = conversationId; }

    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public boolean isAutoReply() { return autoReply; }
    public void setAutoReply(boolean autoReply) { this.autoReply = autoReply; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

