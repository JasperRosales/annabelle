package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.model.Report;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.util.List;
import java.util.function.Consumer;

/**
 * The report details dialog, used by the client, professional and admin dashboards. Admins and
 * the attorney/psychiatrist who received the report also get "Review the report": set the status
 * (changeable at any time, even after Resolved) or add remarks.
 */
public final class ReportDetailsController extends Controller {
    private static final List<String> STEPS = List.of(Report.UNDER_REVIEW, Report.IN_PROGRESS, Report.RESOLVED);

    private Report current;
    private Consumer<Report> onChange = r -> {};

    public ReportDetailsController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("close-report-modal", action ->
                byId("report-modal").removeClass("show").attr("aria-hidden", "true"));
        router.onClick("report-review-open", action -> {
            byId("report-modal-review").hidden(false);
            byId("report-review-toggle").hidden(true);
        });
        router.onClick("report-set-status", action -> save(action.arg(), null));
        router.onClick("report-save-remarks", action -> {
            String remarks = byId("report-modal-new-remarks").trimmedValue();
            if (remarks.isEmpty()) {
                byId("report-modal-review-status").text("Please enter remarks before saving.");
                return;
            }
            save(null, remarks);
        });
    }

    /** Read-only view (clients). */
    public void show(Report r) {
        show(r, ignored -> {});
    }

    /** {@code changed} runs after a review saves, with the updated report. */
    public void show(Report r, Consumer<Report> changed) {
        current = r;
        onChange = changed;
        fill(r);
        Account account = session().account().orElse(null);
        boolean canReview = account != null && (account.isAdmin() || account.isProfessional());
        byId("report-modal-manage").hidden(!canReview);
        byId("report-review-toggle").hidden(false);
        byId("report-modal-review").hidden(true);
        byId("report-modal-new-remarks").value("");
        byId("report-modal-review-status").text("");
        byId("report-modal").addClass("show").attr("aria-hidden", "false");
    }

    private void fill(Report r) {
        byId("report-modal-code").text(orDefault(r.reportCode(), "Report"));
        byId("report-modal-status").attr("class", "status-badge " + r.badgeClass())
                .text(orDefault(r.statusLabel(), "Under Review"));
        byId("report-modal-client").text(orDefault(r.fullName(), "—"));
        byId("report-modal-type").text(orDefault(r.concernType(), "—"));
        byId("report-modal-service").text(recipient(r));
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
        dom().byClass("report-status-pill").forEach(p -> p.toggleClass("active", p.data("arg").equals(r.status())));
    }

    private void save(String status, String remarks) {
        if (current == null) return;
        El message = byId("report-modal-review-status").text("Saving...");
        List<El> buttons = byId("report-modal-review").descendants(el -> "BUTTON".equalsIgnoreCase(el.raw().getTagName()));
        buttons.forEach(b -> b.disabled(true));
        UiCall.of(api().reports().update(current.id(), status, remarks))
                .onSuccess(updated -> {
                    current = updated;
                    fill(updated);
                    byId("report-modal-new-remarks").value("");
                    message.text(status != null ? "Status updated to " + orDefault(updated.statusLabel(), status) + "."
                            : "Remarks added.");
                    onChange.accept(updated);
                })
                .onError(error -> message.text("Could not update the report: " + error.getMessage()))
                .always(() -> buttons.forEach(b -> b.disabled(false)))
                .start();
    }

    private static String recipient(Report r) {
        String who = "PSYCHIATRIST".equals(r.recipient()) || "THOMATT".equals(r.serviceType())
                ? "Psychiatrist (ThoMatt)" : "Attorney (JusticeCare)";
        if (r.serviceType() == null) return "Not yet routed";
        String name = r.assignedProfessionalName();
        return name == null || name.isBlank() ? who + " · awaiting assignment" : who + " · " + name;
    }
}
