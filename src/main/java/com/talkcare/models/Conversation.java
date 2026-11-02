package com.talkcare.models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private int id;
    private int clientId;
    private Integer professionalId;
    private String serviceType;
    private String title;
    private Timestamp createdAt;
    private List<ChatMessage> messages = new ArrayList<>();

    public Conversation() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }

    public Integer getProfessionalId() { return professionalId; }
    public void setProfessionalId(Integer professionalId) { this.professionalId = professionalId; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
}

