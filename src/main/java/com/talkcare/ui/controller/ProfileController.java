package com.talkcare.ui.controller;

import com.talkcare.model.Account;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.Screen;
import com.talkcare.ui.UiCall;

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
        router.onClick("save-profile", action -> save());
        navigator().register(this, "profile");
    }

    @Override
    public void onShow(String viewId) {
        session().account().ifPresent(this::fill);
    }

    private void fill(Account account) {
        byId("profile-username").value(orDefault(account.username(), ""));
        byId("profile-email").value(orDefault(account.email(), ""));
        byId("profile-display-img").src(orDefault(assetUrl(account.profilePictureUrl()), "assets/individual.svg"));
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

    private void save() {
        UiCall.of(api().profile().update(
                        byId("profile-username").trimmedValue(),
                        byId("profile-email").trimmedValue(),
                        byId("profile-password").trimmedValue()))
                .onSuccess(updated -> {
                    session().update(updated);
                    byId("profile-password").value("");
                    dialogs().info("Profile updated successfully!");
                })
                .onError(error -> dialogs().error("Error updating profile: " + error.getMessage()))
                .start();
    }
}
