package com.talkcare.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.talkcare.model.Appointment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Sessions: clients book, professionals confirm/complete/cancel, administrators see them all. */
public final class AppointmentApi {
    private static final TypeReference<List<Appointment>> APPOINTMENTS = new TypeReference<>() {};

    private final ApiClient client;

    AppointmentApi(ApiClient client) {
        this.client = client;
    }

    /** The signed-in client's or professional's sessions; upcoming sessions for administrators. */
    public CompletableFuture<List<Appointment>> list() {
        return client.get("/api/appointments", APPOINTMENTS);
    }

    /** Administrators: every session on one day. */
    public CompletableFuture<List<Appointment>> on(LocalDate day) {
        return client.get("/api/appointments?date=" + day, APPOINTMENTS);
    }

    public CompletableFuture<Appointment> book(Booking booking) {
        return client.post("/api/appointments", booking, Appointment.class);
    }

    public CompletableFuture<Appointment> setStatus(long id, String status) {
        return client.patch("/api/appointments/" + id + "/status", Map.of("status", status), Appointment.class);
    }

    /** Body of POST /api/appointments; {@code start} is a local date-time like 2026-11-05T14:00. */
    public record Booking(String serviceType, String start, int durationMinutes, String notes) {}
}
