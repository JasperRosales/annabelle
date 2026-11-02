package com.talkcare.ui.controller;

import com.talkcare.model.Report;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.dom.El;

import java.util.List;

/** The report details dialog, used by the client and professional dashboards. */
public final class ReportDetailsController extends Controller {
    private static final List<String> STEPS = List.of(Report.UNDER_REVIEW, Report.IN_PROGRESS, Report.RESOLVED);

    public ReportDetailsController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("close-report-modal", action ->
                byId("report-modal").removeClass("show").attr("aria-hidden", "true"));
    }

    public void show(Report r) {
        byId("report-modal-code").text(orDefault(r.reportCode(), "Report"));
        byId("report-modal-status").attr("class", "status-badge " + r.badgeClass())
                .text(orDefault(r.statusLabel(), "Under Review"));
        byId("report-modal-client").text(orDefault(r.fullName(), "—"));
        byId("report-modal-type").text(orDefault(r.concernType(), "—"));
        byId("report-modal-service").text(serviceName(r.serviceType()));
        byId("report-modal-submitted").text(Formats.dateTime(r.createdAt()));
        byId("report-modal-updated").text(Formats.dateTime(r.updatedAt()));
        byId("report-modal-details").text(orDefault(r.concernDetails(), "—"));
        byId("report-modal-witness").text(orDefault(r.involvedWitness(), "None given"));
        byId("report-modal-remarks").text(orDefault(r.remarks(), "No remarks yet."));

        int reached = Math.max(0, STEPS.indexOf(r.status()));
        List<El> steps = byId("report-modal").allByClass("progress-step");
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).toggleClass("done", i < reached).toggleClass("current", i == reached);
        }
        byId("report-modal").addClass("show").attr("aria-hidden", "false");
    }

    private static String serviceName(String code) {
        if ("THOMATT".equals(code)) return "ThoMatt";
        if ("JUSTICE_CARE".equals(code)) return "JusticeCare";
        return "Not yet routed";
    }
}
