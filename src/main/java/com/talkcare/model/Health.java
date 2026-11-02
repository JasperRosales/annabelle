package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** API and database reachability (GET /api/health). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Health(boolean ok, String message) {}
