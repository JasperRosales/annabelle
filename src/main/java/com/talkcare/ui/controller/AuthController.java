package com.talkcare.ui.controller;

import com.talkcare.api.ApiException;
import com.talkcare.api.AuthApi;
import com.talkcare.model.Account;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

import java.util.concurrent.CompletableFuture;

/** Role-specific sign in and registration, sign out, and password reset requests. */
public final class AuthController extends Controller {
    private final VerificationController verification;

    public AuthController(UiContext context, VerificationController verification) {
        super(context);
        this.verification = verification;
    }

    @Override
    public void bind(ActionRouter router) {
        router.onSubmit("login", action -> login(action.source(), action.arg()));
        router.onSubmit("register", action -> register(action.source(), action.arg()));
        router.onClick("logout", action -> logout());
        router.onClick("forgot-password", action -> openForgotPassword(action.source()));
        router.onClick("close-forgot-password", action -> closeForgotPassword());
        router.onSubmit("request-password-reset", action -> requestPasswordReset(action.source()));
    }

    /** Every role has its own sign-in form; {@code data-arg} is the role the API must match. */
    private void login(El form, String role) {
        El status = form.firstByClass("auth-form-status").text("");
        El button = submitButton(form).disabled(true).text("Signing in...");
        El password = field(form, "password");

        UiCall.of(api().auth().login(field(form, "username").trimmedValue(), password.value(), role))
                .onSuccess(account -> {
                    session().signIn(account);
                    password.value("");
                    navigator().goHome();
                })
                .onError(error -> status.text(message(error, "Invalid account name or password.")))
                .always(() -> button.disabled(false).text("Sign in"))
                .start();
    }

    /** Client, attorney and psychiatrist registration; all continue to ID verification. */
    private void register(El form, String role) {
        String fullName = field(form, "fullName").trimmedValue();
        String username = field(form, "username").trimmedValue();
        String address = field(form, "address").trimmedValue();
        String contactNo = field(form, "contactNo").trimmedValue();
        String email = field(form, "email").trimmedValue();
        String password = field(form, "password").value();
        CompletableFuture<Account> request = Account.ROLE_CLIENT.equals(role)
                ? api().auth().register(new AuthApi.Registration(fullName, username, address, contactNo, email, password))
                : api().auth().registerProfessional(role, new AuthApi.ProfessionalRegistration(
                        fullName, username, address, contactNo, email, password,
                        field(form, "licenseNo").trimmedValue(), field(form, "affiliation").trimmedValue()));

        El status = form.firstByClass("auth-form-status").text("");
        El button = submitButton(form).disabled(true).text("Creating account...");
        UiCall.of(request)
                .onSuccess(account -> {
                    session().signIn(account);
                    form.resetForm();
                    verification.prepare(account, email, address);
                    navigator().navigate("id-verify");
                })
                .onError(error -> status.text(message(error, "Please check your details and try again.")))
                .always(() -> button.disabled(false).text("Create account"))
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

    /** Pre-fills the email when the sign-in form it was opened from already holds one. */
    private void openForgotPassword(El link) {
        String login = link.closest(el -> el.hasAttr("data-submit"))
                .map(form -> field(form, "username").trimmedValue()).orElse("");
        El email = byId("forgot-password-email").value(login.contains("@") ? login : "");
        byId("forgot-password-new").value("");
        byId("forgot-password-confirm").value("");
        byId("forgot-password-status").text("");
        byId("forgot-password-modal").addClass("show").attr("aria-hidden", "false");
        email.focus();
    }

    private void closeForgotPassword() {
        byId("forgot-password-modal").removeClass("show").attr("aria-hidden", "true");
    }

    private void requestPasswordReset(El form) {
        String newPassword = byId("forgot-password-new").value();
        El status = byId("forgot-password-status");
        if (!newPassword.equals(byId("forgot-password-confirm").value())) {
            status.text("The passwords do not match.");
            return;
        }
        status.text("Sending request…");
        El button = submitButton(form).disabled(true);
        UiCall.of(api().auth().requestPasswordReset(byId("forgot-password-email").trimmedValue(), newPassword))
                .onSuccess(answer -> {
                    byId("forgot-password-new").value("");
                    byId("forgot-password-confirm").value("");
                    status.text(answer.message());
                })
                .onError(error -> status.text(message(error, "Could not send the request.")))
                .always(() -> button.disabled(false))
                .start();
    }

    /** Server error text, or a fallback; unreachable servers get the connection hint. */
    static String message(ApiException error, String fallback) {
        if (error.isUnreachable()) return error.getMessage();
        return orDefault(error.getMessage(), fallback);
    }
}
