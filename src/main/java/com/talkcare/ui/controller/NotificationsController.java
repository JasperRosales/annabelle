package com.talkcare.ui.controller;

import com.talkcare.model.Notification;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Poller;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.List;

/**
 * The navbar bell: unread count, the notification panel, and live pop-up toasts. Polls every
 * 4 s while signed in; each notification newer than the last one seen pops up as a toast.
 */
public final class NotificationsController extends Controller {
    private final Poller poller = new Poller(Duration.seconds(4));
    private List<Notification> latest = List.of();
    private long newestSeen = -1;
    private final List<Runnable> newListeners = new java.util.ArrayList<>();

    public NotificationsController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("toggle-notifications", action -> toggle());
        router.onClick("mark-notifications-read", action -> markAllRead());
        router.onClick("open-notifications", action -> {
            action.source().remove();
            if (!panel().hasClass("show")) toggle();
        });
        router.observeClicks(target -> {
            El bell = byId("nav-bell");
            if (!panel().contains(target) && !bell.contains(target)) close();
        });
        navigator().beforeNavigate(this::close);
        session().addListener(this::sessionChanged);
    }

    /** Runs when a notification newer than the last one seen arrives (not on the first load). */
    public void onNew(Runnable listener) {
        newListeners.add(listener);
    }

    /** Reloads now (e.g. after an action that creates notifications). */
    public void refresh() {
        if (!session().isSignedIn()) return;
        UiCall.of(api().notifications().list())
                .onSuccess(list -> {
                    latest = list;
                    long unread = list.stream().filter(n -> !n.read()).count();
                    byId("notif-badge").hidden(unread == 0).text(unread > 9 ? "9+" : String.valueOf(unread));
                    if (panel().hasClass("show")) render();
                    long newest = list.stream().mapToLong(Notification::id).max().orElse(0);
                    boolean first = newestSeen < 0;
                    long seen = newestSeen;
                    newestSeen = Math.max(newestSeen, newest);
                    if (first) {
                        if (unread > 0) toast(unread == 1 ? "You have 1 unread notification."
                                : "You have " + unread + " unread notifications.");
                    } else if (newest > seen) {
                        list.stream().filter(n -> n.id() > seen && !n.read()).limit(3).toList()
                                .reversed().forEach(n -> toast(n.message()));
                        newListeners.forEach(Runnable::run);
                    }
                })
                .onError(error -> {
                    if (panel().hasClass("show") && latest.isEmpty()) {
                        byId("notif-list").clear().append(
                                dom().create("div", "admin-request-error", "Could not load notifications."));
                    }
                })
                .start();
    }

    private void sessionChanged() {
        if (session().isSignedIn()) {
            refresh();
            poller.start(this::refresh);
        } else {
            poller.stop();
            latest = List.of();
            newestSeen = -1;
            byId("notif-badge").hidden(true);
            close();
        }
    }

    private void toggle() {
        if (panel().toggleClass("show")) {
            render();
            refresh();
        }
    }

    private void render() {
        El list = byId("notif-list").clear();
        if (latest.isEmpty()) {
            list.append(dom().create("div", "dsh-empty compact").append(
                    dom().create("strong").text("You're all caught up"),
                    dom().create("span").text("Updates about your account, reports, sessions and replies appear here.")));
            return;
        }
        for (Notification n : latest) {
            list.append(dom().create("div", "notif-item" + (n.read() ? "" : " unread")).append(
                    dom().create("span", "notif-dot"),
                    dom().create("div").append(
                            dom().create("div", "notif-text", n.message()),
                            dom().create("div", "notif-time", Formats.relative(n.createdAt())))));
        }
    }

    private void markAllRead() {
        UiCall.of(api().notifications().markAllRead()).onSuccess(ignored -> refresh()).start();
    }

    /** A pop-up in the corner that opens the panel when clicked and fades out after a few seconds. */
    private void toast(String message) {
        El stack = dom().findById("notif-toasts").orElseGet(() -> {
            El created = dom().create("div", "notif-toasts").attr("id", "notif-toasts");
            dom().body().append(created);
            return created;
        });
        while (stack.allByClass("notif-toast").size() >= 3) stack.firstByClass("notif-toast").remove();
        El toast = dom().create("div", "notif-toast").attr("data-action", "open-notifications").append(
                dom().create("span", "notif-toast-title", "New notification"),
                dom().create("span", "notif-toast-text", message));
        stack.append(toast);
        PauseTransition hide = new PauseTransition(Duration.seconds(7));
        hide.setOnFinished(e -> toast.remove());
        hide.play();
    }

    private El panel() {
        return byId("notif-panel");
    }

    private void close() {
        dom().findById("notif-panel").ifPresent(p -> p.removeClass("show"));
    }
}
