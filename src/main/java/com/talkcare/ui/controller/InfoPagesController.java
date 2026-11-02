package com.talkcare.ui.controller;

import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.Screen;
import com.talkcare.ui.dom.El;

/** The informational pages: About-us tabs and the Contact form. */
public final class InfoPagesController extends Controller implements Screen {

    public InfoPagesController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("about-tab", action -> showAboutTab(action.source(), action.arg()));
        router.onClick("contact-submit", action -> submitContact());
        navigator().register(this, "contact");
    }

    /** Each tab's text is a {@code .about-tab-panel[data-tab]} in about.html; only one is visible. */
    private void showAboutTab(El button, String tab) {
        dom().byClass("about-tab-btn").forEach(b -> b.removeClass("active"));
        button.addClass("active");
        for (El panel : byId("about-tab-text").allByClass("about-tab-panel")) {
            boolean selected = tab.equals(panel.data("tab"));
            panel.hidden(!selected);
            if (selected) {
                byId("about-tab-image").src(panel.data("image")).attr("alt", panel.data("image-alt"));
            }
        }
    }

    /** Pre-fills the sender for signed-in users. */
    @Override
    public void onShow(String viewId) {
        byId("contact-status").text("");
        session().account().ifPresent(account -> {
            if (byId("contact-name").trimmedValue().isEmpty()) byId("contact-name").value(account.displayName());
            if (byId("contact-email").trimmedValue().isEmpty()) byId("contact-email").value(orDefault(account.email(), ""));
        });
    }

    private void submitContact() {
        El status = byId("contact-status").text("");
        String name = byId("contact-name").trimmedValue();
        String email = byId("contact-email").trimmedValue();
        El message = byId("contact-message-text");
        if (name.isEmpty() || email.isEmpty() || message.trimmedValue().isEmpty()) {
            status.text("Please enter your name, email and message.");
            return;
        }
        UiCall.of(api().profile().contact(name, email, message.trimmedValue()))
                .onSuccess(ignored -> {
                    message.value("");
                    status.text("Thank you! Your message was sent to the TalkCare team.");
                })
                .onError(error -> status.text(AuthController.message(error, "Could not send your message.")))
                .start();
    }
}
