package com.talkcare.ui.controller;

import com.talkcare.api.AppointmentApi;
import com.talkcare.model.Appointment;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sessions shared by every dashboard: the "Book a session" dialog (clients), the session rows
 * with their role-specific actions, and status changes. Dashboards render lists through
 * {@link #render} and reload when {@link #onChange} fires.
 */
public final class AppointmentsController extends Controller {
    /** Whose dashboard shows the list: decides the counterpart's name and the actions offered. */
    public enum Viewer { CLIENT, PROFESSIONAL, ADMIN }

    private final List<Runnable> listeners = new ArrayList<>();

    public AppointmentsController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("open-book-session", action -> openBooking(action.arg()));
        router.onClick("close-book-session", action -> closeBooking());
        router.onSubmit("book-session", action -> book(action.source()));
        router.onClick("session-status", action -> {
            String[] parts = action.arg().split(":", 2);
            changeStatus(Long.parseLong(parts[0]), parts[1]);
        });
    }

    /** Called after a session is booked or changes status. */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /** Fills a list container with session rows, or an empty state. */
    public void render(El container, List<Appointment> sessions, Viewer viewer, String emptyText) {
        container.clear();
        if (sessions.isEmpty()) {
            El empty = dom().create("div", "dsh-empty compact").append(
                    dom().create("strong").text("No sessions"),
                    dom().create("span").text(emptyText));
            if (viewer == Viewer.CLIENT) {
                empty.append(dom().create("button", "dsh-btn secondary small", "Book a session")
                        .attr("type", "button").attr("data-action", "open-book-session"));
            }
            container.append(empty);
            return;
        }
        for (Appointment session : sessions) container.append(row(session, viewer));
    }

    private El row(Appointment s, Viewer viewer) {
        String[] tile = Formats.dayTile(s.start());
        String with = switch (viewer) {
            case CLIENT -> "with " + orDefault(s.professionalName(), "your professional");
            case PROFESSIONAL -> "with " + orDefault(s.clientName(), "Client #" + s.clientId());
            case ADMIN -> orDefault(s.clientName(), "Client") + " with " + orDefault(s.professionalName(), "professional");
        };
        El main = dom().create("div", "session-main").append(
                dom().create("div", "session-title", s.service().displayName() + " session " + with),
                dom().create("div", "session-time", Formats.timeRange(s.start(), s.end())));
        if (!orDefault(s.notes(), "").isEmpty()) main.append(dom().create("div", "session-notes", s.notes()));

        El actions = dom().create("div", "session-actions");
        if (s.isActive()) {
            if (viewer == Viewer.PROFESSIONAL) {
                if (Appointment.SCHEDULED.equals(s.status())) actions.append(action(s, Appointment.CONFIRMED, "Confirm", "primary"));
                if (!Formats.isFuture(s.start())) {
                    actions.append(action(s, Appointment.COMPLETED, "Complete", "secondary"));
                    actions.append(action(s, Appointment.NO_SHOW, "No-show", "ghost"));
                }
            }
            actions.append(action(s, Appointment.CANCELLED, "Cancel", "ghost"));
        }
        return dom().create("div", "session-row").append(
                dom().create("div", "date-tile").append(
                        dom().create("span").text(tile[0]), dom().create("strong").text(tile[1])),
                main,
                dom().create("div", "session-side").append(
                        dom().create("span", "status-badge " + s.badgeClass(), orDefault(s.statusLabel(), s.status())),
                        actions));
    }

    private El action(Appointment s, String status, String label, String style) {
        return dom().create("button", "dsh-btn small " + style, label)
                .attr("type", "button")
                .attr("data-action", "session-status")
                .attr("data-arg", s.id() + ":" + status);
    }

    private void changeStatus(long id, String status) {
        if (Appointment.CANCELLED.equals(status) && !dialogs().confirm("Cancel this session?")) return;
        UiCall.of(api().appointments().setStatus(id, status))
                .onSuccess(updated -> listeners.forEach(Runnable::run))
                .onError(error -> dialogs().error(AuthController.message(error, "Could not update the session.")))
                .start();
    }

    // ---- booking dialog ---------------------------------------------------------------------

    private void openBooking(String service) {
        if (!session().isSignedIn() || !session().require().isClient()) return;
        El form = byId("book-session-form");
        form.resetForm();
        if (service != null && !service.isBlank()) field(form, "serviceType").value(service);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        field(form, "date").attr("min", LocalDate.now().toString()).value(tomorrow.toString());
        form.firstByClass("tc-form-status").text("");
        byId("book-session-modal").addClass("show").attr("aria-hidden", "false");
    }

    private void closeBooking() {
        byId("book-session-modal").removeClass("show").attr("aria-hidden", "true");
    }

    private void book(El form) {
        El status = form.firstByClass("tc-form-status").text("");
        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(field(form, "date").trimmedValue());
            time = LocalTime.parse(field(form, "time").value());
        } catch (DateTimeParseException e) {
            status.text("Please choose a valid date and time.");
            return;
        }
        AppointmentApi.Booking booking = new AppointmentApi.Booking(
                field(form, "serviceType").value(),
                date.atTime(time).toString(),
                Integer.parseInt(field(form, "duration").value()),
                field(form, "notes").trimmedValue());
        El button = submitButton(form).disabled(true).text("Requesting...");
        UiCall.of(api().appointments().book(booking))
                .onSuccess(created -> {
                    closeBooking();
                    listeners.forEach(Runnable::run);
                    dialogs().info("Session requested for " + Formats.timeRange(created.start(), created.end())
                            + ". " + orDefault(created.professionalName(), "Your professional")
                            + " will confirm it.");
                })
                .onError(error -> status.text(AuthController.message(error, "Could not book the session.")))
                .always(() -> button.disabled(false).text("Request session"))
                .start();
    }
}
