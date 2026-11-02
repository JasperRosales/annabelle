package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.dom.El;

/** Top bar: guest vs signed-in links, the user menu, search, and plain navigation links. */
public final class NavbarController extends Controller {

    public NavbarController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("navigate", action -> navigator().navigate(action.arg()));
        router.onClick("toggle-user-dropdown", action -> menu().toggleClass("show"));
        router.onClick("search", action -> search());
        router.observeClicks(target -> {
            El pill = dom().byClass("nav-user-pill").getFirst();
            if (!menu().contains(target) && !pill.contains(target)) closeMenu();
        });
        navigator().beforeNavigate(this::closeMenu);
        session().addListener(this::refresh);
    }

    /** Shows the guest or the signed-in navbar to match the session. */
    public void refresh() {
        boolean signedIn = session().isSignedIn();
        byId("guest-nav").display(signedIn ? "none" : "flex");
        byId("auth-nav").display(signedIn ? "flex" : "none");
        byId("guest-buttons").display(signedIn ? "none" : "flex");
        byId("auth-user-bar").display(signedIn ? "flex" : "none");
        if (!signedIn) return;

        Account account = session().require();
        byId("nav-user-name").text(account.displayName());
        byId("dropdown-user-name").text(account.displayName());
        byId("dropdown-user-role").text(account.roleLabel());
        String avatar = orDefault(assetUrl(account.profilePictureUrl()), "assets/individual.svg");
        byId("nav-avatar-img").src(avatar);
        byId("dropdown-avatar-img").src(avatar);
    }

    /** Shows a local preview (e.g. a just-picked photo) before the upload finishes. */
    public void previewAvatar(String url) {
        byId("nav-avatar-img").src(url);
        byId("dropdown-avatar-img").src(url);
    }

    private void search() {
        dialogs().prompt("Search TalkCare resources, articles, or cases:").ifPresent(query -> {
            dialogs().info("Searching TalkCare Knowledge Base for \"" + query + "\"...");
            navigator().navigate("about");
        });
    }

    private El menu() {
        return byId("user-dropdown-menu");
    }

    private void closeMenu() {
        menu().removeClass("show");
    }
}
