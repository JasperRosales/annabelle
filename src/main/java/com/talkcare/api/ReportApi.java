package com.talkcare.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.talkcare.model.Report;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Reports/concerns filed by clients. */
public final class ReportApi {
    private static final TypeReference<List<Report>> REPORTS = new TypeReference<>() {};

    private final ApiClient client;

    ReportApi(ApiClient client) {
        this.client = client;
    }

    /** @param statusFilter "All", "Under Review", "In Progress" or "Resolved" */
    public CompletableFuture<List<Report>> list(String statusFilter) {
        return client.get("/api/reports?status=" + ApiClient.query(statusFilter), REPORTS);
    }

    public CompletableFuture<List<Report>> all() {
        return client.get("/api/reports", REPORTS);
    }

    /** Sets the status (code, or null to keep it) and/or appends remarks. Admins and the receiving professional. */
    public CompletableFuture<Report> update(long id, String status, String remarks) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (status != null) body.put("status", status);
        if (remarks != null) body.put("remarks", remarks);
        return client.patch("/api/reports/" + id + "/status", body, Report.class);
    }

    public CompletableFuture<Report> submit(NewReport report) {
        return client.post("/api/reports", report, Report.class);
    }

    /** Body of POST /api/reports. */
    public record NewReport(String fullName, String email, String address, String contactNo,
                            String concernDetails, String concernType, String involvedWitness,
                            String recipient) {}
}
