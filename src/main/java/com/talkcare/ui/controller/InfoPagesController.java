package com.talkcare.ui.controller;

import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.dom.El;

/** The informational pages: About-us tabs and the Contact form. */
public final class InfoPagesController extends Controller {

    public InfoPagesController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("about-tab", action -> showAboutTab(action.source(), action.arg()));
        router.onClick("contact-submit", action -> submitContact());
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

    private void submitContact() {
        El message = byId("contact-message-text");
        if (message.trimmedValue().isEmpty()) {
            dialogs().info("Please write your message.");
            return;
        }
        dialogs().info("Thank you! Your message has been dispatched to TalkCare Support.");
        message.value("");
        navigator().navigate("home");
    }
}
