package com.talkcare.ui.controller;

import com.talkcare.api.ReportApi;
import com.talkcare.model.Report;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.util.List;
import java.util.Map;

/** Client dashboard: activity history of reports (with status filters) and the report form. */
public final class ReportsController extends Controller implements Screen {
    /** Filter label → pill id and the API status it counts. */
    private static final Map<String, String[]> PILLS = Map.of(
            "All", new String[] {"pill-all", null},
            "Under Review", new String[] {"pill-review", Report.UNDER_REVIEW},
            "In Progress", new String[] {"pill-progress", Report.IN_PROGRESS},
            "Resolved", new String[] {"pill-resolved", Report.RESOLVED});

    private String filter = "All";
    private List<Report> shown = List.of();

    public ReportsController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("filter-reports", action -> {
            if (action.source().hasClass("activity-pill")) {
                dom().byClass("activity-pill").forEach(pill -> pill.removeClass("active"));
                action.source().addClass("active");
            }
            filter = action.arg();
            reload();
        });
        router.onClick("report-details", action -> showDetails(Long.parseLong(action.arg())));
        router.onSubmit("submit-report", action -> submit(action.source()));
        navigator().register(this, "dashboard");
    }

    @Override
    public void onShow(String viewId) {
        reload();
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
            container.append(dom().create("div").attr("style", "text-align: center; padding: 24px; color: #557053;")
                    .text("No reports found under \"" + filter + "\"."));
            return;
        }
        for (Report report : shown) {
            String label = orDefault(report.statusLabel(), "Under Review");
            El left = dom().create("div", "activity-item-left").append(
                    dom().create("span", "activity-doc-icon", "📄"),
                    dom().create("div").append(
                            dom().create("div", "activity-item-code", orDefault(report.reportCode(), "Report")),
                            dom().create("div", "activity-item-status", label + ".")));
            El badge = dom().create("div").append(dom().create("span", "status-badge " + report.badgeClass(), label));
            container.append(dom().create("div", "activity-item-row")
                    .attr("data-action", "report-details")
                    .attr("data-arg", String.valueOf(report.id()))
                    .append(left, badge));
        }
    }

    private void updateCounts() {
        UiCall.of(api().reports().all())
                .onSuccess(all -> PILLS.forEach((label, pill) -> {
                    long count = pill[1] == null ? all.size()
                            : all.stream().filter(r -> pill[1].equals(r.status())).count();
                    byId(pill[0]).text(label + " (" + count + ")");
                }))
                .start();
    }

    private void showDetails(long id) {
        shown.stream().filter(r -> r.id() == id).findFirst().ifPresent(r -> dialogs().info(
                "Report: " + r.reportCode()
                        + "\nClient: " + r.fullName()
                        + "\nConcern: " + r.concernType()
                        + "\nStatus: " + r.statusLabel()
                        + "\nDetails: " + r.concernDetails()
                        + (r.remarks() == null || r.remarks().isBlank() ? "" : "\nRemarks: " + r.remarks())));
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
