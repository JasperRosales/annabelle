package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One line of the admin "Recent Activity" timeline (GET /api/admin/activity). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityEntry(String action, String details, String createdAt) {}
