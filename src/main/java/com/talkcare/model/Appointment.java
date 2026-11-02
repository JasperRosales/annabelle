package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A session between a client and their assigned professional (GET/POST /api/appointments). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Appointment(long id, long clientId, String clientName, long professionalId, String professionalName,
                         String serviceType, String start, String end, String status, String statusLabel,
                         String notes) {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";

    /** Requested or confirmed: it can still be confirmed, completed or cancelled. */
    public boolean isActive() {
        return SCHEDULED.equals(status) || CONFIRMED.equals(status);
    }

    public ServiceType service() {
        return ServiceType.fromCode(serviceType).orElse(ServiceType.JUSTICE_CARE);
    }

    /** CSS modifier for the status badge. */
    public String badgeClass() {
        return switch (status == null ? "" : status) {
            case CONFIRMED -> "confirmed";
            case COMPLETED -> "resolved";
            case CANCELLED, NO_SHOW -> "cancelled";
            default -> "under-review";
        };
    }
}
