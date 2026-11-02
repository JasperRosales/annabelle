package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.model.ActivityEntry;
import com.talkcare.model.AdminRequest;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Administrator dashboard: metrics, pending verifications/reports with a review dialog,
 * recent activity, user list and the appointment calendar.
 */
public final class AdminController extends Controller implements Screen {
    private static final List<String> VERIFICATION_ROWS = List.of(
            "review-id-type-row", "review-id-number-row", "review-address-row", "review-contact-row");
    private static final List<String> REPORT_ROWS = List.of(
            "review-report-code-row", "review-concern-type-row", "review-details-row",
            "review-witness-row", "review-existing-remarks-row");

    private final AdminCalendar calendar;
    private List<AdminRequest> requests = List.of();
    private AdminRequest reviewing;

    public AdminController(UiContext context) {
        super(context);
        this.calendar = new AdminCalendar(context.dom());
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

    private void loadPendingRequests() {
        UiCall.of(api().admin().pendingRequests())
                .onSuccess(list -> {
                    requests = list;
                    El container = byId("admin-pending-requests-list").clear();
                    if (list.isEmpty()) {
                        container.append(dom().create("div", "admin-request-empty", "No pending requests"));
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
        if (request.isVerification()) {
            title = orDefault(request.name(), "Verification request");
            note = orDefault(request.idType(), "ID")
                    + (orDefault(request.idNumber(), "").isEmpty() ? "" : " • " + request.idNumber())
                    + " • Submitted " + Formats.dateTime(request.submittedAt());
        } else {
            title = orDefault(request.reportCode(), "Report/Concern");
            note = orDefault(request.name(), "Client") + " • " + orDefault(request.concernType(), "Report")
                    + " • Submitted " + Formats.dateTime(request.submittedAt());
        }
        El info = dom().create("div", "admin-request-info").append(dom().create("div").append(
                dom().create("div", "admin-request-title", title),
                dom().create("div", "admin-request-note", note)));
        if (request.isVerification() && !orDefault(request.imageUrl(), "").isEmpty()) {
            info.append(dom().create("img", "admin-request-image")
                    .src(assetUrl(request.imageUrl())).attr("alt", "Submitted ID document"));
        }
        El review = dom().create("button", "btn-admin-review", "Review")
                .attr("type", "button")
                .attr("data-action", "admin-open-review")
                .attr("data-arg", String.valueOf(index));
        return dom().create("div", "admin-request-row").append(info, review);
    }

    private void openReview(AdminRequest request) {
        reviewing = request;
        boolean verification = request.isVerification();
        setField("review-name", request.name());
        setField("review-email", request.email());
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
                        container.append(dom().create("div", "admin-activity-empty", "No recent activity"));
                        return;
                    }
                    for (ActivityEntry entry : list) {
                        String what = orDefault(entry.details(), orDefault(entry.action(), "Activity"));
                        container.append(dom().create("div", "admin-timeline-item").append(
                                dom().create("span", "timeline-circle"),
                                dom().create("span").text(what + " • " + Formats.dateTime(entry.createdAt()))));
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
    }

    private void loadUsers() {
        UiCall.of(api().admin().users())
                .onSuccess(users -> {
                    El container = byId("admin-active-users-list").clear();
                    for (Account user : users) container.append(userRow(user));
                })
                .onError(error -> showError("admin-active-users-list", "Unable to load user profiles: " + error.getMessage()))
                .start();
    }

    private El userRow(Account user) {
        String fallback = switch (orDefault(user.role(), "")) {
            case Account.ROLE_ATTORNEY -> "assets/avatar-case-handler.svg";
            case Account.ROLE_PSYCHIATRIST -> "assets/avatar-therapist.svg";
            default -> "assets/individual.svg";
        };
        El details = dom().create("div").attr("style", "font-size: 12px; color: #557053;")
                .appendText(orDefault(user.email(), "") + " • ")
                .append(dom().create("b").text(user.roleLabel()))
                .appendText(" • Status: " + orDefault(user.status(), "VERIFIED"));
        return dom().create("div", "admin-user-row").append(
                dom().create("img", "admin-user-thumb").src(orDefault(assetUrl(user.profilePictureUrl()), fallback))
                        .attr("alt", "Avatar"),
                dom().create("div").attr("style", "flex: 1;").append(
                        dom().create("div").attr("style", "font-weight: 800; font-size: 15px;").text(user.displayName()),
                        details));
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
