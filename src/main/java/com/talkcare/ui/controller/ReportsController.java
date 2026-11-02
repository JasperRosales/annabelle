package com.talkcare.ui.controller;

import com.talkcare.api.ReportApi;
import com.talkcare.model.Account;
import com.talkcare.model.Report;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.util.List;

/** Client dashboard: activity history of reports (with status filters) and the report form. */
public final class ReportsController extends Controller implements Screen {
    /** A status filter: its tab, its summary card, and the API status it counts (null = all). */
    private record Filter(String label, String pillId, String kpiId, String status) {}

    private static final List<Filter> FILTERS = List.of(
            new Filter("All", "pill-all", "kpi-total", null),
            new Filter("Under review", "pill-review", "kpi-review", Report.UNDER_REVIEW),
            new Filter("In progress", "pill-progress", "kpi-progress", Report.IN_PROGRESS),
            new Filter("Resolved", "pill-resolved", "kpi-resolved", Report.RESOLVED));

    private final AppointmentsController appointments;
    private final ReportDetailsController details;
    private String filter = "All";
    private boolean pastSessions;
    private List<Report> shown = List.of();

    public ReportsController(UiContext context, AppointmentsController appointments, ReportDetailsController details) {
        super(context);
        this.appointments = appointments;
        this.details = details;
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("filter-reports", action -> {
            filter = action.arg();
            FILTERS.forEach(f -> byId(f.pillId()).toggleClass("active", f.label().equalsIgnoreCase(filter)));
            if (!navigator().currentView().equals("dashboard")) navigator().navigate("dashboard");
            else reload();
        });
        router.onClick("report-details", action -> showDetails(Long.parseLong(action.arg())));
        router.onSubmit("submit-report", action -> submit(action.source()));
        appointments.onChange(() -> {
            if ("dashboard".equals(navigator().currentView())) loadSessions();
        });
        router.onClick("client-sessions-tab", action -> {
            pastSessions = "past".equals(action.arg());
            dom().byClass("client-session-tab").forEach(t -> t.toggleClass("active", action.arg().equals(t.data("arg"))));
            loadSessions();
        });
        navigator().register(this, "dashboard", "report-form");
    }

    @Override
    public void onShow(String viewId) {
        Account account = session().require();
        if ("report-form".equals(viewId)) {
            prefillReport(account);
            return;
        }
        byId("dash-user-name").text(account.displayName());
        El chip = byId("dash-account-status").removeClass("verified").removeClass("pending");
        if (account.isVerified()) chip.addClass("verified").text("Verified account");
        else if (account.isAwaitingReview()) chip.addClass("pending").text("Verification in review");
        else chip.text("Not yet verified");
        reload();
        loadSessions();
    }

    /** The report form starts with the signed-in client's details. */
    private void prefillReport(Account account) {
        byId("report-name").value(orDefault(account.fullName(), account.displayName()));
        byId("report-email").value(orDefault(account.email(), ""));
        byId("report-address").value(orDefault(account.address(), ""));
        byId("report-contact").value(orDefault(account.contactNo(), ""));
        byId("report-submit-status").text("");
    }

    /** Upcoming: requested/confirmed sessions still ahead. Past: everything else, newest first. */
    private void loadSessions() {
        UiCall.of(api().appointments().list())
                .onSuccess(list -> appointments.render(byId("client-sessions-list"),
                        list.stream().filter(a -> pastSessions != (a.isActive() && Formats.isFuture(a.end())))
                                .limit(pastSessions ? 8 : 4).toList(),
                        AppointmentsController.Viewer.CLIENT, pastSessions
                                ? "Finished and cancelled sessions appear here."
                                : "Book a time with your professional."))
                .onError(error -> byId("client-sessions-list").clear()
                        .append(dom().create("div", "admin-request-error", "Could not load sessions.")))
                .start();
    }

    public void reload() {
        UiCall.of(api().reports().list(filter))
                .onSuccess(list -> {
                    shown = list;
                    render();
                    updateCounts();
                })
                .onError(error -> System.err.println("Reports load error: " + error.getMessage()))
                .start();
    }

    private void render() {
        El container = byId("activity-reports-list").clear();
        if (shown.isEmpty()) {
            container.append(dom().create("div", "dsh-empty").append(
                    dom().create("strong").text("No reports here yet"),
                    dom().create("span").text("All".equals(filter)
                            ? "When you report a concern, it will appear here with its status."
                            : "You have no reports marked \"" + filter + "\".")));
            return;
        }
        for (Report report : shown) {
            String label = orDefault(report.statusLabel(), "Under Review");
            El reportCell = dom().create("div", "report-cell-main").append(
                    dom().create("span", "report-icon"),
                    dom().create("div").append(
                            dom().create("div", "report-code", orDefault(report.reportCode(), "Report")),
                            dom().create("div", "report-meta", orDefault(report.fullName(), ""))));
            container.append(dom().create("div", "report-row")
                    .attr("data-action", "report-details")
                    .attr("data-arg", String.valueOf(report.id()))
                    .append(reportCell,
                            dom().create("div", "report-concern", orDefault(report.concernType(), "—")),
                            dom().create("div").append(dom().create("span", "status-badge " + report.badgeClass(), label)),
                            dom().create("div", "report-chevron", "\u203A")));
        }
    }

    private void updateCounts() {
        UiCall.of(api().reports().all())
                .onSuccess(all -> FILTERS.forEach(f -> {
                    long count = f.status() == null ? all.size()
                            : all.stream().filter(r -> f.status().equals(r.status())).count();
                    byId(f.pillId()).text(f.label() + " (" + count + ")");
                    byId(f.kpiId()).text(String.valueOf(count));
                }))
                .start();
    }

    private void showDetails(long id) {
        shown.stream().filter(r -> r.id() == id).findFirst().ifPresent(details::show);
    }

    private void submit(El form) {
        ReportApi.NewReport report = new ReportApi.NewReport(
                byId("report-name").trimmedValue(),
                byId("report-email").trimmedValue(),
                byId("report-address").trimmedValue(),
                byId("report-contact").trimmedValue(),
                byId("report-details").trimmedValue(),
                byId("report-type").value(),
                byId("report-witness").trimmedValue());
        El status = byId("report-submit-status").text("");
        El button = submitButton(form).disabled(true).text("SUBMITTING...");
        String fallback = "Hindi naipasa ang report. Pakisubukan ulit.";

        UiCall.of(api().reports().submit(report))
                .onSuccess(created -> {
                    String code = orDefault(created.reportCode(), "");
                    byId("report-success-title").text("Your Report has been successfully submitted"
                            + (code.isEmpty() ? "" : " (" + code + ")") + "!");
                    form.resetForm();
                    navigator().navigate("report-success");
                    reload();
                })
                .onError(error -> status.text(AuthController.message(error, fallback)))
                .always(() -> button.disabled(false).text("SUBMIT"))
                .start();
    }
}
