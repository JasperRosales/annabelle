package com.talkcare.ui.controller;

import com.talkcare.model.Conversation;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Poller;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;
import javafx.util.Duration;

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

    public ProfessionalInboxController(UiContext context) {
        super(context);
        this.messages = new MessageListView(context.dom(), context.session(), context.api().client());
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("professional-select", action -> select(Long.parseLong(action.arg())));
        router.onSubmit("professional-reply", action -> reply());
        navigator().register(this, VIEWS);
    }

    @Override
    public void onShow(String viewId) {
        String name = session().require().displayName();
        dom().byClass("role-dashboard-user-name").forEach(el -> el.text(name));
        refresh();
        poller.start(this::refresh);
    }

    @Override
    public void onHide() {
        poller.stop();
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
