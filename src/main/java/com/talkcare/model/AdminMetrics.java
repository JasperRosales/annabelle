package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Counters for the admin dashboard footer (GET /api/admin/metrics). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminMetrics(Integer totalUsers, Integer pendingApprovals, Integer sessionToday) {}
