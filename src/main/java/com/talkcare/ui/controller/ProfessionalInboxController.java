package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.model.Appointment;
import com.talkcare.model.Report;
import com.talkcare.ui.Formats;
import com.talkcare.model.Conversation;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Poller;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Attorney and psychiatrist dashboards: the conversations assigned to the professional, the
 * selected thread, and replies. The inbox refreshes every few seconds while it is open.
 */
public final class ProfessionalInboxController extends Controller implements Screen {
    private static final String[] VIEWS = {"professional-dashboard", "case-handler-dashboard"};

    private final MessageListView messages;
    private final Poller poller = new Poller(Duration.seconds(4));
    private List<Conversation> conversations = List.of();
    private Long selectedId;

    private final AppointmentsController appointments;
    private final ReportDetailsController details;
    private List<Report> reports = List.of();

    public ProfessionalInboxController(UiContext context, AppointmentsController appointments,
                                       ReportDetailsController details) {
        super(context);
        this.appointments = appointments;
        this.details = details;
        this.messages = new MessageListView(context.dom(), context.session(), context.api().client());
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("professional-select", action -> select(Long.parseLong(action.arg())));
        router.onSubmit("professional-reply", action -> reply());
        router.onClick("pro-report-details", action -> reports.stream()
                .filter(r -> r.id() == Long.parseLong(action.arg())).findFirst()
                .ifPresent(r -> details.show(r, updated -> loadReports())));
        appointments.onChange(() -> {
            if (List.of(VIEWS).contains(navigator().currentView())) loadSessions();
        });
        navigator().register(this, VIEWS);
    }

    @Override
    public void onShow(String viewId) {
        Account account = session().require();
        dom().byClass("role-dashboard-user-name").forEach(el -> el.text(account.displayName()));
        dom().findById("view-" + viewId).ifPresent(view -> showApproval(view, account));
        refresh();
        loadSessions();
        loadReports();
        poller.start(this::refresh);
    }

    @Override
    public void onHide() {
        poller.stop();
    }

    private Optional<El> view() {
        return dom().findById("view-" + session().homeView());
    }

    /** Active sessions (upcoming, or past but not yet completed) first, then a few finished ones. */
    private void loadSessions() {
        view().ifPresent(view -> UiCall.of(api().appointments().list())
                .onSuccess(list -> {
                    List<Appointment> active = list.stream().filter(Appointment::isActive).toList();
                    List<Appointment> shown = new ArrayList<>(active);
                    list.stream().filter(a -> !a.isActive()).limit(Math.max(0, 6 - active.size())).forEach(shown::add);
                    view.firstByClass("pro-kpi-sessions").text(String.valueOf(
                            active.stream().filter(a -> Formats.isFuture(a.end())).count()));
                    appointments.render(view.firstByClass("pro-sessions-list"), shown,
                            AppointmentsController.Viewer.PROFESSIONAL, "Session requests from your clients appear here.");
                })
                .onError(error -> view.firstByClass("pro-sessions-list").clear()
                        .append(dom().create("div", "admin-request-error", "Could not load sessions.")))
                .start());
    }

    private void loadReports() {
        view().ifPresent(view -> UiCall.of(api().reports().all())
                .onSuccess(list -> {
                    reports = list;
                    view.firstByClass("pro-kpi-reports").text(String.valueOf(list.stream().filter(Report::isOpen).count()));
                    El container = view.firstByClass("pro-reports-list").clear();
                    if (list.isEmpty()) {
                        container.append(dom().create("div", "dsh-empty compact").append(
                                dom().create("strong").text("No reports sent to you"),
                                dom().create("span").text("Reports clients send to you will be listed here.")));
                        return;
                    }
                    for (Report r : list) {
                        container.append(dom().create("div", "report-row compact")
                                .attr("data-action", "pro-report-details")
                                .attr("data-arg", String.valueOf(r.id()))
                                .append(dom().create("div", "report-cell-main").append(
                                                dom().create("span", "report-icon"),
                                                dom().create("div").append(
                                                        dom().create("div", "report-code", orDefault(r.reportCode(), "Report")),
                                                        dom().create("div", "report-meta",
                                                                orDefault(r.concernType(), "") + " · " + Formats.relative(r.createdAt())))),
                                        dom().create("div").append(dom().create("span",
                                                "status-badge " + r.badgeClass(), orDefault(r.statusLabel(), "Under Review"))),
                                        dom().create("div", "report-action").append(
                                                dom().create("span", "report-review-btn", "Review"))));
                    }
                })
                .onError(error -> view.firstByClass("pro-reports-list").clear()
                        .append(dom().create("div", "admin-request-error", "Could not load reports.")))
                .start());
    }

    /** Self-registered professionals get clients only after an administrator approves their ID. */
    private void showApproval(El view, Account account) {
        view.firstByClass("pro-kpi-status").text(account.isVerified() ? "Verified"
                : account.isAwaitingReview() ? "In review" : "Not verified");
        El banner = view.firstByClass("pro-approval").hidden(account.isVerified());
        if (account.isVerified()) return;
        boolean submitted = account.isAwaitingReview();
        banner.firstByClass("pro-approval-title").text(submitted
                ? "Your account is awaiting approval"
                : "Verify your identity to start receiving clients");
        banner.firstByClass("pro-approval-text").text(submitted
                ? "An administrator is reviewing your ID and license number, usually within 24 hours. "
                        + "Clients are assigned to you once it is approved."
                : "Upload a valid ID (IBP or PRC ID is fine). An administrator reviews it together with your "
                        + "license number before clients are assigned to you.");
        banner.firstByClass("pro-approval-action").hidden(submitted);
    }

    /** The inbox section inside the dashboard the API assigned to this professional. */
    private Optional<El> inbox() {
        return dom().findById("view-" + session().homeView())
                .flatMap(view -> view.allByClass("professional-inbox").stream().findFirst());
    }

    private void refresh() {
        inbox().ifPresent(inbox -> UiCall.of(api().chat().assigned())
                .onSuccess(list -> {
                    conversations = list;
                    dom().byClass("pro-kpi-conversations").forEach(el -> el.text(String.valueOf(list.size())));
                    if (selectedId != null && find(selectedId).isEmpty()) selectedId = null;
                    renderList(inbox);
                    if (selectedId == null && !list.isEmpty()) select(list.getFirst().id());
                    else if (selectedId != null) loadMessages(inbox, true);
                    else showNothingSelected(inbox);
                    inbox.firstByClass("professional-inbox-status").text("");
                })
                .onError(error -> inbox.firstByClass("professional-inbox-status")
                        .text("Could not load messages: " + error.getMessage()))
                .start());
    }

    private void renderList(El inbox) {
        El list = inbox.firstByClass("professional-conversation-list").clear();
        if (conversations.isEmpty()) {
            list.append(dom().create("p", "professional-inbox-empty",
                    "No assigned conversations yet. New client messages will appear here."));
            return;
        }
        for (Conversation conversation : conversations) {
            El button = dom().create("button", "professional-conversation-button")
                    .attr("type", "button")
                    .attr("data-action", "professional-select")
                    .attr("data-arg", String.valueOf(conversation.id()))
                    .append(dom().create("strong").text(conversation.clientLabel()),
                            dom().create("span").text(conversation.service().displayName()));
            if (selectedId != null && conversation.id() == selectedId) button.addClass("active");
            list.append(button);
        }
    }

    private void select(long conversationId) {
        Optional<Conversation> conversation = find(conversationId);
        Optional<El> inbox = inbox();
        if (conversation.isEmpty() || inbox.isEmpty()) return;

        selectedId = conversationId;
        El box = inbox.get();
        box.firstByClass("professional-active-title")
                .text(conversation.get().service().displayName() + " · " + conversation.get().clientLabel());
        box.firstByClass("professional-reply-input").disabled(false);
        box.firstByClass("professional-reply-send").disabled(false);
        renderList(box);
        loadMessages(box, false);
    }

    private void showNothingSelected(El inbox) {
        inbox.firstByClass("professional-active-title").text("No assigned conversations yet");
        inbox.firstByClass("professional-message-list").clear();
        inbox.firstByClass("professional-reply-input").disabled(true);
        inbox.firstByClass("professional-reply-send").disabled(true);
    }

    private void loadMessages(El inbox, boolean silent) {
        if (selectedId == null) return;
        long requested = selectedId;
        Conversation conversation = find(requested).orElseThrow();
        UiCall.of(api().chat().messages(requested))
                .onSuccess(list -> {
                    if (selectedId != null && selectedId == requested) {
                        messages.render(inbox.firstByClass("professional-message-list"), list, conversation.service());
                    }
                })
                .onError(error -> {
                    if (!silent) {
                        inbox.firstByClass("professional-inbox-status")
                                .text("Could not load this conversation: " + error.getMessage());
                    }
                })
                .start();
    }

    private void reply() {
        Optional<El> found = inbox();
        if (found.isEmpty() || selectedId == null) return;
        El inbox = found.get();
        El input = inbox.firstByClass("professional-reply-input");
        String text = input.trimmedValue();
        if (text.isEmpty()) return;

        El button = inbox.firstByClass("professional-reply-send").disabled(true);
        El status = inbox.firstByClass("professional-inbox-status").text("");
        UiCall.of(api().chat().reply(selectedId, text))
                .onSuccess(ignored -> {
                    input.value("");
                    loadMessages(inbox, true);
                })
                .onError(error -> status.text("Reply not sent: " + error.getMessage()))
                .always(() -> button.disabled(false))
                .start();
    }

    private Optional<Conversation> find(long id) {
        return conversations.stream().filter(c -> c.id() == id).findFirst();
    }
}
