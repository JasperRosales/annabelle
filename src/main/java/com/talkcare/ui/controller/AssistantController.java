package com.talkcare.ui.controller;

import com.talkcare.model.ChatbotConversation;
import com.talkcare.model.Message;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

/**
 * The TalkCare Chatbot screen: a third chat next to JusticeCare and ThoMatt. The API answers
 * with Gemini behind its guardrails and keeps the conversation (in memory, cleared on logout).
 */
public final class AssistantController extends Controller implements Screen {
    private static final String BOT_AVATAR = "assets/talkcare-logo.png";
    private static final String FALLBACK_GREETING = "Hi, I'm the TalkCare Chatbot. How can I help you today?";

    private ChatbotConversation shown = new ChatbotConversation(FALLBACK_GREETING, java.util.List.of());
    private boolean sending;

    public AssistantController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onSubmit("assistant-send", action -> send(action.source()));
        router.onClick("assistant-clear", action -> clear());
        navigator().register(this, "assistant");
    }

    @Override
    public void onShow(String viewId) {
        status("");
        render(new ChatbotConversation(FALLBACK_GREETING, java.util.List.of()));
        UiCall.of(api().chatbot().conversation())
                .onSuccess(this::render)
                .onError(error -> status("Could not load the chatbot: " + error.getMessage()))
                .start();
    }

    @Override
    public void onHide() {}

    private void send(El form) {
        El input = byId("assistant-input");
        String text = input.trimmedValue();
        if (text.isEmpty() || sending) return;
        sending = true;
        status("");
        El button = submitButton(form).disabled(true);
        input.value("");
        box().append(row(true, text), row(false, "Typing...").addClass("assistant-typing"));
        box().scrollToBottom();

        UiCall.of(api().chatbot().send(text))
                .onSuccess(this::render)
                .onError(error -> {
                    render(shown); // drop the pending bubbles
                    if (input.trimmedValue().isEmpty()) input.value(text);
                    status(error.getMessage());
                })
                .always(() -> {
                    sending = false;
                    button.disabled(false);
                })
                .start();
    }

    private void clear() {
        if (sending) return;
        UiCall.of(api().chatbot().reset())
                .onSuccess(conversation -> {
                    status("");
                    render(conversation);
                })
                .onError(error -> status(error.getMessage()))
                .start();
    }

    private void render(ChatbotConversation conversation) {
        shown = conversation;
        El box = box().clear();
        box.append(row(false, orDefault(conversation.greeting(), FALLBACK_GREETING)));
        for (Message message : conversation.messages()) box.append(row(message.isFromClient(), message.messageText()));
        box.scrollToBottom();
    }

    private El row(boolean fromUser, String text) {
        String avatar = fromUser
                ? session().account().map(a -> assetUrl(a.profilePictureUrl())).filter(u -> !u.isBlank())
                        .orElse("assets/individual.svg")
                : BOT_AVATAR;
        return dom().create("div", "message-row " + (fromUser ? "client" : "professional"))
                .append(dom().create("img", "message-avatar-small").src(avatar).attr("alt", ""),
                        dom().create("div").append(dom().create("div", "message-bubble").append(
                                dom().create("div").text(text))));
    }

    private void status(String text) {
        byId("assistant-status").text(text);
    }

    private El box() {
        return byId("assistant-messages-box");
    }
}
