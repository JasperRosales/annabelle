package com.talkcare.ui.controller;

import com.talkcare.model.Message;
import com.talkcare.model.ServiceType;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.ImageFile;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.Poller;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * A client's chat with a JusticeCare or ThoMatt professional. Sending a message returns the
 * server's auto-reply; new replies are picked up by polling every few seconds.
 */
public final class ChatController extends Controller implements Screen {
    private final MessageListView messages;
    private final Poller poller = new Poller(Duration.seconds(4));
    private ServiceType service = ServiceType.JUSTICE_CARE;
    private Long conversationId;
    private ImageFile attachment;

    public ChatController(UiContext context) {
        super(context);
        this.messages = new MessageListView(context.dom(), context.session(), context.api().client());
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("open-service-chat", action -> ServiceType.fromCode(action.arg()).ifPresentOrElse(this::open,
                () -> {
                    navigator().navigate("chat");
                    messages.notice(box(), "Please choose a valid TalkCare service.");
                }));
        router.onClick("chat-reload", action -> loadMessages(false));
        router.onSubmit("send-message", action -> send(action.source()));
        router.onClick("pick-chat-photo", action -> pickPhoto());
        router.onClick("chat-remove-attachment", action -> setAttachment(null));
        router.onInput("filter-chat-threads", action -> filterThreads(action.source().value()));
        router.onClick("open-professional-modal", action -> openProfessionalModal());
        router.onClick("close-professional-modal", action -> byId("prof-modal").removeClass("show"));
        navigator().register(this, "chat");
    }

    @Override
    public void onShow(String viewId) {
        loadMessages(false);
        startPolling();
    }

    @Override
    public void onHide() {
        poller.stop();
    }

    private void open(ServiceType chosen) {
        service = chosen;
        conversationId = null;
        navigator().navigate("chat");
        resetForm();
        showService();
        El threadName = addThread();
        messages.notice(box(), "Loading your conversation...");

        UiCall.of(api().chat().openConversation(chosen))
                .onSuccess(conversation -> {
                    if (service != chosen) return; // the user already switched to another service
                    conversationId = conversation.id();
                    if (!conversation.professionalName().isBlank()) {
                        byId("chat-header-name").text(conversation.professionalName());
                        threadName.text(conversation.professionalName());
                    }
                    loadMessages(false);
                    startPolling();
                })
                .onError(error -> messages.notice(box(), "Could not load this conversation: " + error.getMessage()))
                .start();
    }

    private void resetForm() {
        attachment = null;
        renderAttachment();
        byId("chat-text-input").value("").attr("placeholder", service.placeholder());
        byId("chat-send-status").text("");
        byId("chat-search-box").value("");
        box().clear();
    }

    private void showService() {
        byId("chat-service-seal").src(service.seal());
        byId("chat-service-header-title").text(service.displayName());
        byId("chat-header-avatar").src(service.avatar());
        byId("chat-header-name").text(service.professionalName());
    }

    /** The sidebar entry for the open conversation; returns its name label. */
    private El addThread() {
        El name = dom().create("span", "chat-thread-name", service.professionalName());
        El thread = dom().create("button", "chat-thread-item active")
                .attr("type", "button")
                .attr("data-action", "chat-reload")
                .append(dom().create("img", "chat-thread-avatar").src(service.avatar()).attr("alt", ""),
                        dom().create("span", "chat-thread-meta")
                                .append(name, dom().create("span", "chat-thread-preview", service.preview())));
        byId("chat-thread-list").replaceChildren(thread);
        return name;
    }

    private void startPolling() {
        poller.start(() -> loadMessages(true));
    }

    private void loadMessages(boolean silent) {
        if (conversationId == null) return;
        long requested = conversationId;
        UiCall.of(api().chat().messages(requested))
                .onSuccess(list -> {
                    if (conversationId != null && conversationId == requested) show(list);
                })
                .onError(error -> {
                    if (!silent) messages.notice(box(), error.getMessage());
                })
                .start();
    }

    private void show(List<Message> list) {
        messages.render(box(), list, service);
    }

    private void send(El form) {
        El input = byId("chat-text-input");
        String text = input.trimmedValue();
        if (text.isEmpty() && attachment == null) return;
        if (conversationId == null) {
            dialogs().info("Please select a service before sending a message.");
            return;
        }
        El button = submitButton(form).disabled(true);
        El status = byId("chat-send-status").text("");
        UiCall.of(api().chat().send(conversationId, text, attachment == null ? "" : attachment.dataUrl()))
                .onSuccess(list -> {
                    show(list);
                    input.value("");
                    setAttachment(null);
                    loadMessages(true);
                })
                .onError(error -> status.text("Message not sent: " + error.getMessage()))
                .always(() -> button.disabled(false))
                .start();
    }

    private void pickPhoto() {
        images().choose("Attach a photo", ImagePicker.PHOTO_TYPES).ifPresent(this::setAttachment);
    }

    private void setAttachment(ImageFile file) {
        attachment = file;
        renderAttachment();
    }

    private void renderAttachment() {
        El preview = byId("chat-attachment-preview").clear().hidden(attachment == null);
        if (attachment == null) return;
        preview.append(
                dom().create("span").text("Photo ready to send: " + attachment.name()),
                dom().create("button").attr("type", "button").attr("data-action", "chat-remove-attachment").text("Remove"));
    }

    private void filterThreads(String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        dom().byClass("chat-thread-item").forEach(item ->
                item.display(item.text().toLowerCase(Locale.ROOT).contains(needle) ? "flex" : "none"));
    }

    private void openProfessionalModal() {
        byId("prof-modal-img").src(service.avatar());
        byId("prof-modal-title-name").text(service.professionalName());
        byId("prof-modal-subtag").text(service.professionalTitle());
        byId("prof-cert-wrap").display(service.showsCertificate() ? "block" : "none");
        byId("prof-modal").addClass("show");
    }

    private El box() {
        return byId("chat-messages-box");
    }
}
