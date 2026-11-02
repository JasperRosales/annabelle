package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The stored location of an uploaded image (POST /api/profile/photo). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadedFile(String url) {}
