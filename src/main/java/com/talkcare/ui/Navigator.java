package com.talkcare.ui;

import com.talkcare.ui.dom.Dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Switches between the {@code <section class="view-screen" id="view-...">} screens, applies
 * the access rules (signed-out users, role home pages), and tells the owning {@link Screen}
 * when its view is shown or left.
 */
public final class Navigator {
    private final Dom dom;
    private final Session session;
    private final Map<String, Screen> screens = new HashMap<>();
    private final List<Runnable> beforeNavigate = new ArrayList<>();
    private Screen active;
    private String currentView = "";

    public Navigator(Dom dom, Session session) {
        this.dom = dom;
        this.session = session;
    }

    public void register(Screen screen, String... viewIds) {
        for (String viewId : viewIds) screens.put(viewId, screen);
    }

    public void beforeNavigate(Runnable hook) {
        beforeNavigate.add(hook);
    }

    public String currentView() {
        return currentView;
    }

    public void goHome() {
        navigate(session.homeView());
    }

    public void navigate(String requested) {
        String viewId = resolve(requested);
        if (active != null) {
            active.onHide();
            active = null;
        }
        beforeNavigate.forEach(Runnable::run);

        dom.byClass("view-screen").forEach(screen -> screen.removeClass("active"));
        dom.all(el -> el.hasClass("nav-links")).forEach(list ->
                list.descendants(el -> "A".equalsIgnoreCase(el.raw().getTagName()))
                        .forEach(link -> link.removeClass("active")));
        dom.findById("nav-item-" + viewId).ifPresent(link -> link.addClass("active"));

        dom.findById("view-" + viewId).ifPresent(target -> {
            target.addClass("active");
            dom.scrollToTop();
        });
        dom.body().toggleClass("page-welcome", "welcome".equals(viewId));
        currentView = viewId;

        Screen screen = screens.get(viewId);
        if (screen != null) {
            active = screen;
            screen.onShow(viewId);
        }
    }

    /** Access rules: signed-out visitors and each role only reach the screens meant for them. */
    private String resolve(String viewId) {
        if (session.isSignedIn()) {
            String home = session.homeView();
            if ("home".equals(viewId)) return home;
            if ("dashboard".equals(viewId) && !session.require().isClient()) return home;
            if ("admin".equals(viewId) && !"admin".equals(home)) return home;
            return viewId;
        }
        if ("home".equals(viewId)) return "welcome";      // Home = the welcome page, like the logo
        if ("services".equals(viewId)) return "login";    // Services is for signed-in users only
        return viewId;
    }
}
