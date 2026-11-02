package com.talkcare.ui.controller;

import com.talkcare.api.ApiException;
import com.talkcare.api.AuthApi;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

/** Sign in, create account, sign out, and the two password-reset steps. */
public final class AuthController extends Controller {
    private final VerificationController verification;

    public AuthController(UiContext context, VerificationController verification) {
        super(context);
        this.verification = verification;
    }

    @Override
    public void bind(ActionRouter router) {
        router.onSubmit("login", action -> login(action.source()));
        router.onSubmit("register", action -> register(action.source()));
        router.onClick("logout", action -> logout());
        router.onClick("forgot-password", action -> openForgotPassword());
        router.onClick("close-forgot-password", action -> closeForgotPassword());
        router.onSubmit("request-password-reset", action -> requestPasswordReset(action.source()));
        router.onSubmit("reset-password", action -> resetPassword(action.source()));
    }

    private void login(El form) {
        El status = byId("login-status").text("");
        El button = submitButton(form).disabled(true).text("Signing in...");

        UiCall.of(api().auth().login(byId("login-username").trimmedValue(), byId("login-password").value()))
                .onSuccess(account -> {
                    session().signIn(account);
                    byId("login-password").value("");
                    navigator().goHome();
                })
                .onError(error -> status.text(message(error, "Invalid account name or password.")))
                .always(() -> button.disabled(false).text("SUBMIT"))
                .start();
    }

    private void register(El form) {
        AuthApi.Registration registration = new AuthApi.Registration(
                byId("reg-name").trimmedValue(),
                byId("reg-username").trimmedValue(),
                byId("reg-address").trimmedValue(),
                byId("reg-contact").trimmedValue(),
                byId("reg-email").trimmedValue(),
                byId("reg-password").value());
        El status = byId("register-status").text("");
        El button = submitButton(form).disabled(true).text("Creating account...");

        UiCall.of(api().auth().register(registration))
                .onSuccess(account -> {
                    session().signIn(account);
                    byId("reg-password").value("");
                    verification.prepare(account, registration);
                    navigator().navigate("id-verify");
                })
                .onError(error -> status.text(message(error, "Please check your details and try again.")))
                .always(() -> button.disabled(false).text("Create Account"))
                .start();
    }

    private void logout() {
        if (session().isSignedIn()) {
            // Tokens are stateless JWTs: signing out locally is enough even if this call fails.
            UiCall.of(api().auth().logout()).start();
        }
        session().signOut();
        navigator().navigate("welcome");
    }

    private void openForgotPassword() {
        String login = byId("login-username").trimmedValue();
        El email = byId("forgot-password-email").value(login.contains("@") ? login : "");
        byId("forgot-password-status").text("");
        byId("forgot-password-modal").addClass("show").attr("aria-hidden", "false");
        email.focus();
    }

    private void closeForgotPassword() {
        byId("forgot-password-modal").removeClass("show").attr("aria-hidden", "true");
    }

    private void requestPasswordReset(El form) {
        El status = byId("forgot-password-status").text("Sending reset link…");
        El button = submitButton(form).disabled(true);
        UiCall.of(api().auth().requestPasswordReset(byId("forgot-password-email").trimmedValue()))
                .onSuccess(answer -> status.text(answer.message()))
                .onError(error -> status.text(message(error, "Could not send the reset link.")))
                .always(() -> button.disabled(false))
                .start();
    }

    /** Opens the "choose a new password" screen for a token from a reset email. */
    public void startPasswordReset(String token) {
        byId("password-reset-token").value(token);
        byId("password-reset-status").text("");
        navigator().navigate("reset-password");
    }

    private void resetPassword(El form) {
        String newPassword = byId("reset-new-password").value();
        El status = byId("password-reset-status");
        if (!newPassword.equals(byId("reset-confirm-password").value())) {
            status.text("The passwords do not match.");
            return;
        }
        El button = submitButton(form).disabled(true);
        status.text("Updating password…");
        UiCall.of(api().auth().resetPassword(byId("password-reset-token").value(), newPassword))
                .onSuccess(answer -> {
                    byId("login-username").value("");
                    byId("login-password").value("");
                    navigator().navigate("login");
                    dialogs().info(answer.message());
                })
                .onError(error -> status.text(message(error, "Could not update the password.")))
                .always(() -> button.disabled(false))
                .start();
    }

    /** Server error text, or a fallback; unreachable servers get the connection hint. */
    static String message(ApiException error, String fallback) {
        if (error.isUnreachable()) return error.getMessage();
        return orDefault(error.getMessage(), fallback);
    }
}
