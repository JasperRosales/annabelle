package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A plain { "message": ... } answer, e.g. from the password reset endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerMessage(String message) {}
