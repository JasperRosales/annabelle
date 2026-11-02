package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A pending identity verification or under-review report (GET /api/admin/pending-requests). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminRequest(String type, long id, String name, String email, String idType, String idNumber,
                           String address, String contactNo, String imageUrl, String submittedAt,
                           String reportCode, String concernType, String details, String witness,
                           String remarks) {

    public boolean isVerification() {
        return "VERIFICATION".equals(type);
    }
}
