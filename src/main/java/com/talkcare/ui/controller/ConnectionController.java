package com.talkcare.ui.controller;

import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

/**
 * The "Online/Offline" badge and the server dialog. The desktop app never talks to the database;
 * it only reports whether the TalkCare API (and the database behind it) is reachable.
 */
public final class ConnectionController extends Controller {

    public ConnectionController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("open-db-modal", action -> {
            byId("db-modal").addClass("show");
            check(() -> {});
        });
        router.onClick("close-db-modal", action -> byId("db-modal").removeClass("show"));
        router.onClick("retry-api-connection", action -> check(() -> {
            if (session().isSignedIn()) navigator().goHome();
        }));
    }

    /** Checks the API in the background; never blocks the UI (a sleeping host can take a minute). */
    public void check(Runnable onConnected) {
        El text = byId("db-status-text");
        byId("api-base-url").text(api().client().baseUrl());
        text.text("Connecting…");
        byId("db-dialog-status-box").text(
                "Connecting to the TalkCare server. The first connection can take up to a minute while it wakes up.");

        UiCall.of(api().health().check())
                .onSuccess(health -> {
                    show(health.ok(), orDefault(health.message(), health.ok() ? "Connected." : "The server is not ready."));
                    if (health.ok()) onConnected.run();
                })
                .onError(error -> show(false, error.isUnreachable()
                        ? "The TalkCare server could not be reached. Check your internet connection or the API address."
                        : error.getMessage()))
                .start();
    }

    private void show(boolean connected, String message) {
        byId("db-status-dot").attr("class", connected ? "db-dot" : "db-dot disconnected");
        byId("db-status-text").text(connected ? "Online" : "Offline");
        byId("db-dialog-status-box").clear()
                .append(dom().create("b").text("Status: "))
                .appendText(message);
    }
}
