package com.talkcare.ui.controller;

import com.talkcare.model.Notification;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Formats;
import com.talkcare.ui.Poller;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;
import javafx.util.Duration;

import java.util.List;

/** The navbar bell: unread count (refreshed every 10 s while signed in) and the notification panel. */
public final class NotificationsController extends Controller {
    private final Poller poller = new Poller(Duration.seconds(10));
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
                    boolean arrived = newestSeen >= 0 && newest > newestSeen;
                    newestSeen = Math.max(newestSeen, newest);
                    if (arrived) newListeners.forEach(Runnable::run);
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

    private El panel() {
        return byId("notif-panel");
    }

    private void close() {
        dom().findById("notif-panel").ifPresent(p -> p.removeClass("show"));
    }
}
