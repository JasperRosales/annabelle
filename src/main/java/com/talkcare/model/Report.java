package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A report/concern filed by a client (GET/POST /api/reports). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Report(long id, String reportCode, String fullName, String concernType, String concernDetails,
                     String status, String statusLabel, String remarks) {

    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String RESOLVED = "RESOLVED";

    /** CSS modifier for the status badge. */
    public String badgeClass() {
        if (IN_PROGRESS.equals(status)) return "in-progress";
        if (RESOLVED.equals(status)) return "resolved";
        return "under-review";
    }
}
