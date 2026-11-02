package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An in-app notification (GET /api/notifications). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Notification(long id, String type, String message,
                           @JsonProperty("is_read") boolean read,
                           @JsonProperty("created_at") String createdAt) {}
