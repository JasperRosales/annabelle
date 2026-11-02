package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.model.ActivityEntry;
import com.talkcare.model.AdminRequest;
import com.talkcare.model.Report;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Administrator dashboard: metrics, pending verifications/reports with a review dialog,
 * recent activity, user list and the appointment calendar.
 */
public final class AdminController extends Controller implements Screen {
    private static final List<String> VERIFICATION_ROWS = List.of(
            "review-role-row", "review-id-type-row", "review-id-number-row", "review-address-row", "review-contact-row");
    private static final List<String> REPORT_ROWS = List.of(
            "review-report-code-row", "review-concern-type-row", "review-details-row",
            "review-witness-row", "review-existing-remarks-row");

    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);

    private final AdminCalendar calendar;
    private final AppointmentsController appointments;
    private List<AdminRequest> requests = List.of();
    private AdminRequest reviewing;
    private List<Account> users = List.of();
    private String userRole = "ALL";
    private String userQuery = "";

    public AdminController(UiContext context, AppointmentsController appointments) {
        super(context);
        this.calendar = new AdminCalendar(context.dom());
        this.appointments = appointments;
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("admin-open-review", action -> openReview(requests.get(Integer.parseInt(action.arg()))));
        router.onClick("close-admin-review-modal", action -> closeReview());
        router.onClick("show-admin-reject-reason", action ->
                showAll("review-reason-label", "review-reason", "review-confirm-reject"));
        router.onClick("show-admin-remarks", action ->
                showAll("review-remarks-label", "review-remarks", "review-save-remarks"));
        router.onClick("admin-review", action -> submitReview(action.arg()));
        router.onClick("admin-calendar-prev", action -> { calendar.shift(-1); loadSchedule(); });
        router.onClick("admin-calendar-next", action -> { calendar.shift(1); loadSchedule(); });
        router.onClick("admin-calendar-day", action -> {
            calendar.select(Integer.parseInt(action.arg()));
            loadSchedule();
        });
        router.onClick("admin-user-role", action -> {
            userRole = action.arg();
            dom().byClass("admin-role-pill").forEach(p -> p.toggleClass("active", userRole.equals(p.data("arg"))));
            renderUsers();
        });
        router.onClick("admin-user-status", action -> {
            String[] parts = action.arg().split(":", 2);
            setUserStatus(Long.parseLong(parts[0]), parts[1]);
        });
        router.onInput("admin-user-search", action -> {
            userQuery = action.source().trimmedValue().toLowerCase(Locale.ROOT);
            renderUsers();
        });
        appointments.onChange(() -> {
            if ("admin".equals(navigator().currentView())) {
                loadMetrics();
                loadSchedule();
            }
        });
        navigator().register(this, "admin");
    }

    @Override
    public void onShow(String viewId) {
        reload();
    }

    /** Draws an empty calendar for the current month (before any schedule is loaded). */
    public void renderEmptyCalendar() {
        calendar.render(Set.of());
    }

    private void reload() {
        loadMetrics();
        loadOpenReports();
        loadPendingRequests();
        loadUsers();
        loadActivity();
        loadSchedule();
    }

    private void loadMetrics() {
        UiCall.of(api().admin().metrics())
                .onSuccess(m -> {
                    byId("stat-total-users").text(count(m.totalUsers()));
                    byId("stat-pending-approvals").text(count(m.pendingApprovals()));
                    byId("stat-session-today").text(count(m.sessionToday()));
                })
                .onError(error -> List.of("stat-total-users", "stat-pending-approvals", "stat-session-today")
                        .forEach(id -> byId(id).text("—")))
                .start();
    }

    private void loadOpenReports() {
        UiCall.of(api().reports().all())
                .onSuccess(all -> byId("stat-open-reports").text(String.valueOf(all.stream().filter(Report::isOpen).count())))
                .onError(error -> byId("stat-open-reports").text("—"))
                .start();
    }

    private void loadPendingRequests() {
        UiCall.of(api().admin().pendingRequests())
                .onSuccess(list -> {
                    requests = list;
                    byId("admin-pending-count").text(String.valueOf(list.size()));
                    El container = byId("admin-pending-requests-list").clear();
                    if (list.isEmpty()) {
                        container.append(dom().create("div", "dsh-empty").append(
                            dom().create("strong").text("You're all caught up"),
                            dom().create("span").text("New verifications and reports will appear here.")));
                        return;
                    }
                    for (int i = 0; i < list.size(); i++) container.append(requestRow(list.get(i), i));
                })
                .onError(error -> showError("admin-pending-requests-list",
                        "Unable to load pending requests: " + error.getMessage()))
                .start();
    }

    private El requestRow(AdminRequest request, int index) {
        String title;
        String note;
        String kind;
        if (request.isVerification()) {
            title = orDefault(request.name(), "Verification request");
            kind = request.isProfessional() ? roleName(request.role()) + " verification" : "ID verification";
            note = orDefault(request.idType(), "ID")
                    + (orDefault(request.idNumber(), "").isEmpty() ? "" : " • " + request.idNumber())
                    + (orDefault(request.licenseNo(), "").isEmpty() ? "" : " • License " + request.licenseNo());
        } else {
            title = orDefault(request.reportCode(), "Report/Concern");
            kind = "Report";
            note = orDefault(request.name(), "Client") + " • " + orDefault(request.concernType(), "Report");
        }
        El info = dom().create("div", "admin-request-info");
        if (request.isVerification() && !orDefault(request.imageUrl(), "").isEmpty()) {
            info.append(dom().create("img", "admin-request-image")
                    .src(assetUrl(request.imageUrl())).attr("alt", "Submitted ID document"));
        }
        info.append(dom().create("div").append(
                dom().create("div", "admin-request-title").append(
                        dom().create("span").text(title),
                        dom().create("span", "request-tag " + (request.isVerification() ? "verify" : "report"), kind)),
                dom().create("div", "admin-request-note", note),
                dom().create("div", "admin-request-time", "Submitted " + Formats.dateTime(request.submittedAt()))));
        El review = dom().create("button", "btn-admin-review", "Review")
                .attr("type", "button")
                .attr("data-action", "admin-open-review")
                .attr("data-arg", String.valueOf(index));
        return dom().create("div", "admin-request-row").append(info, review);
    }

    private static String roleName(String role) {
        if (role == null || role.isBlank()) return "Client";
        return role.charAt(0) + role.substring(1).toLowerCase();
    }

    private void openReview(AdminRequest request) {
        reviewing = request;
        boolean verification = request.isVerification();
        setField("review-name", request.name());
        setField("review-email", request.email());
        setField("review-role", roleName(request.role()));
        setField("review-license", request.licenseNo());
        setField("review-submitted", Formats.dateTime(request.submittedAt()));
        setField("review-id-type", request.idType());
        setField("review-id-number", request.idNumber());
        setField("review-address", request.address());
        setField("review-contact", request.contactNo());
        setField("review-report-code", request.reportCode());
        setField("review-concern-type", request.concernType());
        setField("review-details", request.details());
        setField("review-witness", request.witness());
        setField("review-existing-remarks", request.remarks());
        VERIFICATION_ROWS.forEach(id -> byId(id).hidden(!verification));
        REPORT_ROWS.forEach(id -> byId(id).hidden(verification));
        byId("review-license-row").hidden(!verification || !request.isProfessional());

        El image = byId("review-image");
        boolean hasImage = verification && !orDefault(request.imageUrl(), "").isEmpty();
        image.hidden(!hasImage);
        if (hasImage) image.src(assetUrl(request.imageUrl()));
        byId("review-verification-actions").hidden(!verification);
        byId("review-report-actions").hidden(verification);
        hideAll("review-reason-label", "review-reason", "review-confirm-reject",
                "review-remarks-label", "review-remarks", "review-save-remarks");
        byId("review-reason").value("");
        byId("review-remarks").value("");
        byId("admin-review-modal").addClass("show").attr("aria-hidden", "false");
    }

    /** Fills a review field, showing "—" when there is no value. */
    private void setField(String id, String value) {
        byId(id).text(orDefault(value, "—"));
    }

    private void closeReview() {
        byId("admin-review-modal").removeClass("show").attr("aria-hidden", "true");
        reviewing = null;
    }

    private void submitReview(String action) {
        if (reviewing == null) return;
        String reason = byId("review-reason").trimmedValue();
        String remarks = byId("review-remarks").trimmedValue();
        if ("REJECT".equals(action) && reason.isEmpty()) {
            dialogs().info("Please provide a reason for rejection.");
            byId("review-reason").focus();
            return;
        }
        if ("ADD_REMARKS".equals(action) && remarks.isEmpty()) {
            dialogs().info("Please enter remarks before saving.");
            byId("review-remarks").focus();
            return;
        }

        List<El> buttons = byId("admin-review-modal").allByClass("admin-action-button");
        buttons.forEach(b -> b.disabled(true));
        UiCall.of(api().admin().act(reviewing, action, reason, remarks))
                .onSuccess(ignored -> {
                    closeReview();
                    reload();
                    dialogs().info("Request updated successfully.");
                })
                .onError(error -> dialogs().error("Unable to update request: " + error.getMessage()))
                .always(() -> buttons.forEach(b -> b.disabled(false)))
                .start();
    }

    private void loadActivity() {
        UiCall.of(api().admin().activity())
                .onSuccess(list -> {
                    El container = byId("admin-timeline-list").clear();
                    if (list.isEmpty()) {
                        container.append(dom().create("div", "dsh-empty compact").append(
                                dom().create("strong").text("No recent activity")));
                        return;
                    }
                    for (ActivityEntry entry : list) {
                        String what = orDefault(entry.details(), orDefault(entry.action(), "Activity"));
                        container.append(dom().create("div", "admin-timeline-item").append(
                                dom().create("span", "timeline-circle"),
                                dom().create("div").append(
                                        dom().create("div", "timeline-text", what),
                                        dom().create("div", "timeline-time", Formats.dateTime(entry.createdAt())))));
                    }
                })
                .onError(error -> showError("admin-timeline-list", "Unable to load recent activity: " + error.getMessage()))
                .start();
    }

    private void loadSchedule() {
        UiCall.of(api().admin().schedule(calendar.month().getYear(), calendar.month().getMonthValue()))
                .onSuccess(schedule -> calendar.render(schedule.days() == null ? Set.of() : new HashSet<>(schedule.days())))
                .onError(error -> {
                    calendar.render(Set.of());
                    showError("admin-calendar-grid", "Unable to load schedule: " + error.getMessage());
                })
                .start();
        LocalDate day = calendar.selected();
        byId("admin-day-title").text(DAY_TITLE.format(day));
        UiCall.of(api().appointments().on(day))
                .onSuccess(list -> appointments.render(byId("admin-day-sessions"), list,
                        AppointmentsController.Viewer.ADMIN, "Nothing is scheduled on this day."))
                .onError(error -> showError("admin-day-sessions", "Unable to load sessions: " + error.getMessage()))
                .start();
    }

    private void loadUsers() {
        UiCall.of(api().admin().users())
                .onSuccess(list -> {
                    users = list;
                    renderUsers();
                })
                .onError(error -> showError("admin-active-users-list", "Unable to load user profiles: " + error.getMessage()))
                .start();
    }

    private void renderUsers() {
        List<Account> shown = users.stream()
                .filter(u -> "ALL".equals(userRole) || userRole.equals(u.role()))
                .filter(u -> userQuery.isEmpty()
                        || u.displayName().toLowerCase(Locale.ROOT).contains(userQuery)
                        || orDefault(u.email(), "").toLowerCase(Locale.ROOT).contains(userQuery)
                        || orDefault(u.username(), "").toLowerCase(Locale.ROOT).contains(userQuery))
                .toList();
        byId("admin-user-count").text(String.valueOf(shown.size()));
        El container = byId("admin-active-users-list").clear();
        if (shown.isEmpty()) {
            container.append(dom().create("div", "dsh-empty compact").append(
                    dom().create("strong").text("No matching users")));
            return;
        }
        for (Account user : shown) container.append(userRow(user));
    }

    private El userRow(Account user) {
        String fallback = switch (orDefault(user.role(), "")) {
            case Account.ROLE_ATTORNEY -> "assets/avatar-case-handler.svg";
            case Account.ROLE_PSYCHIATRIST -> "assets/avatar-therapist.svg";
            default -> "assets/individual.svg";
        };
        String status = orDefault(user.status(), "VERIFIED");
        El who = dom().create("div", "user-cell").append(
                dom().create("img", "admin-user-thumb").src(orDefault(assetUrl(user.profilePictureUrl()), fallback))
                        .attr("alt", ""),
                dom().create("div").append(
                        dom().create("div", "user-name", user.displayName()),
                        dom().create("div", "user-email", orDefault(user.email(), ""))));
        El actions = dom().create("div", "user-actions");
        if (user.id() != session().require().id()) {
            boolean blocked = "SUSPENDED".equals(status) || "DEACTIVATED".equals(status);
            actions.append(dom().create("button", "dsh-btn small " + (blocked ? "secondary" : "ghost"),
                            blocked ? "Reactivate" : "Suspend")
                    .attr("type", "button")
                    .attr("data-action", "admin-user-status")
                    .attr("data-arg", user.id() + ":" + (blocked ? "REACTIVATE" : "SUSPEND")));
        }
        return dom().create("div", "admin-user-row").append(
                who,
                dom().create("div").append(dom().create("span",
                        "role-tag role-" + orDefault(user.role(), "CLIENT").toLowerCase(), user.roleLabel())),
                dom().create("div").append(dom().create("span",
                        "user-status status-" + status.toLowerCase().replace('_', '-'), statusLabel(status))),
                actions);
    }

    private void setUserStatus(long userId, String action) {
        Account user = users.stream().filter(u -> u.id() == userId).findFirst().orElse(null);
        if (user == null) return;
        if ("SUSPEND".equals(action) && !dialogs().confirm("Suspend " + user.displayName()
                + "? They will be signed out and cannot sign in until reactivated.")) return;
        UiCall.of(api().admin().setUserStatus(userId, action))
                .onSuccess(updated -> {
                    loadUsers();
                    loadActivity();
                })
                .onError(error -> dialogs().error("Could not update the account: " + error.getMessage()))
                .start();
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PENDING_VERIFICATION" -> "Pending";
            case "VERIFIED" -> "Verified";
            case "ACTIVE" -> "Active";
            case "SUSPENDED" -> "Suspended";
            case "DEACTIVATED" -> "Deactivated";
            default -> status;
        };
    }

    private void showError(String containerId, String message) {
        dom().findById(containerId).ifPresent(c -> c.replaceChildren(dom().create("div", "admin-request-error", message)));
    }

    private void showAll(String... ids) {
        for (String id : ids) byId(id).hidden(false);
    }

    private void hideAll(String... ids) {
        for (String id : ids) byId(id).hidden(true);
    }

    private static String count(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }
}
