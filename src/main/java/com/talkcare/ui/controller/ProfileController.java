package com.talkcare.ui.controller;

import com.talkcare.api.ProfileApi;
import com.talkcare.model.Account;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;
import com.talkcare.ui.dom.El;

/** "My profile": photo upload and username/email/password changes. */
public final class ProfileController extends Controller implements Screen {
    private final NavbarController navbar;

    public ProfileController(UiContext context, NavbarController navbar) {
        super(context);
        this.navbar = navbar;
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("pick-profile-photo", action -> pickPhoto());
        router.onSubmit("save-profile", action -> save(action.source()));
        navigator().register(this, "profile");
    }

    @Override
    public void onShow(String viewId) {
        session().account().ifPresent(this::fill);
    }

    private void fill(Account account) {
        El form = form();
        field(form, "fullName").value(orDefault(account.fullName(), ""));
        field(form, "username").value(orDefault(account.username(), ""));
        field(form, "email").value(orDefault(account.email(), ""));
        field(form, "contactNo").value(orDefault(account.contactNo(), ""));
        field(form, "address").value(orDefault(account.address(), ""));
        field(form, "password").value("");
        field(form, "confirmPassword").value("");
        form.firstByClass("tc-form-status").text("");

        byId("profile-display-img").src(orDefault(assetUrl(account.profilePictureUrl()), "assets/individual.svg"));
        byId("profile-summary-name").text(account.displayName());
        byId("profile-summary-username").text("@" + orDefault(account.username(), ""));
        byId("profile-summary-email").text(orDefault(account.email(), "—"));
        byId("profile-summary-role").attr("class", "role-tag role-" + orDefault(account.role(), "client").toLowerCase())
                .text(account.roleLabel());
        String status = orDefault(account.status(), "ACTIVE");
        byId("profile-summary-status").attr("class", "user-status status-" + status.toLowerCase().replace('_', '-'))
                .text(account.isVerified() ? "Verified" : account.isAwaitingReview() ? "Verification in review" : "Not verified");
        byId("profile-summary-id").text(orDefault(account.idType(), "").isEmpty() ? "Not submitted"
                : account.idType() + (orDefault(account.idNumber(), "").isEmpty() ? "" : " · " + account.idNumber()));
        byId("profile-verify-btn").hidden(account.isVerified() || account.isAwaitingReview()
                || Account.ROLE_ADMIN.equals(account.role()));
    }

    private El form() {
        return dom().all(el -> "save-profile".equals(el.data("submit"))).getFirst();
    }

    private void pickPhoto() {
        images().choose("Choose a profile photo", ImagePicker.PHOTO_TYPES).ifPresent(file -> {
            byId("profile-display-img").src(file.dataUrl());
            navbar.previewAvatar(file.dataUrl());
            UiCall.of(api().profile().uploadPhoto(file.dataUrl()))
                    .onSuccess(uploaded -> session().update(session().require().withProfilePicture(uploaded.url())))
                    .onError(error -> {
                        dialogs().error("Could not upload the photo: " + error.getMessage());
                        navbar.refresh();
                        session().account().ifPresent(this::fill);
                    })
                    .start();
        });
    }

    private void save(El form) {
        El status = form.firstByClass("tc-form-status").text("");
        String password = field(form, "password").value();
        if (!password.equals(field(form, "confirmPassword").value())) {
            status.text("The new passwords do not match.");
            return;
        }
        ProfileApi.ProfileUpdate update = new ProfileApi.ProfileUpdate(
                field(form, "fullName").trimmedValue(),
                field(form, "username").trimmedValue(),
                field(form, "email").trimmedValue(),
                field(form, "contactNo").trimmedValue(),
                field(form, "address").trimmedValue(),
                password.isEmpty() ? null : password);
        El button = submitButton(form).disabled(true).text("Saving...");
        UiCall.of(api().profile().update(update))
                .onSuccess(updated -> {
                    session().update(updated);
                    session().account().ifPresent(this::fill);
                    dialogs().info(password.isEmpty() ? "Your profile was updated." : "Your profile and password were updated.");
                })
                .onError(error -> status.text(AuthController.message(error, "Could not update your profile.")))
                .always(() -> button.disabled(false).text("Save changes"))
                .start();
    }
}
