package com.talkcare.ui.controller;

import com.talkcare.api.ApiClient;
import com.talkcare.model.Message;
import com.talkcare.model.ServiceType;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Session;
import com.talkcare.ui.dom.Dom;
import com.talkcare.ui.dom.El;

import java.util.List;

/** Draws chat bubbles into a message container; shared by the client chat and the professional inbox. */
final class MessageListView {
    private static final String EMPTY = "Your messages will appear here. "
            + "Send a message to start the conversation with your assigned professional.";

    private final Dom dom;
    private final Session session;
    private final ApiClient client;

    MessageListView(Dom dom, Session session, ApiClient client) {
        this.dom = dom;
        this.session = session;
        this.client = client;
    }

    void notice(El container, String text) {
        container.replaceChildren(dom.create("div", "chat-empty-state", text));
    }

    void render(El container, List<Message> messages, ServiceType service) {
        if (messages.isEmpty()) {
            notice(container, EMPTY);
            return;
        }
        container.clear();
        for (Message message : messages) container.append(row(message, service));
        container.scrollToBottom();
    }

    private El row(Message message, ServiceType service) {
        El row = dom.create("div", "message-row " + side(message));

        El avatar = dom.create("img", "message-avatar-small").src(avatarFor(message, service)).attr("alt", "");
        El bubble = dom.create("div", "message-bubble");
        if (message.hasText()) bubble.append(dom.create("div").text(message.messageText()));
        if (message.hasAttachment()) {
            bubble.append(dom.create("img", "message-attachment")
                    .src(client.resolveAssetUrl(message.attachmentUrl()))
                    .attr("alt", "Photo attached to this message"));
        }
        El content = dom.create("div").append(bubble, dom.create("div", "message-time", Formats.time(message.createdAt())));
        return row.append(avatar, content);
    }

    /** "client" = my own messages (right), "professional" = the other side, "system" = auto-replies. */
    private String side(Message message) {
        if (message.isSystem()) return "system";
        boolean own = session.isProfessional() ? message.isFromProfessional() : message.isFromClient();
        return own ? "client" : "professional";
    }

    private String avatarFor(Message message, ServiceType service) {
        if (!message.isFromClient()) return service.avatar();
        if (!session.isProfessional()) {
            String own = session.account().map(a -> client.resolveAssetUrl(a.profilePictureUrl())).orElse("");
            if (!own.isBlank()) return own;
        }
        return "assets/individual.svg";
    }
}
