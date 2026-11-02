package com.talkcare.ui.controller;

import com.talkcare.api.ProfileApi;
import com.talkcare.model.Account;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.ImageFile;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.UiCall;

/** ID verification after registration: pick an ID photo, fill the form, submit for review. */
public final class VerificationController extends Controller {
    private ImageFile idDocument;

    public VerificationController(UiContext context) {
        super(context);
    }

    @Override
    public void bind(ActionRouter router) {
        router.onClick("pick-id-photo", action -> pickIdPhoto());
        router.onSubmit("submit-verification", action -> submit());
    }

    /** Pre-fills the form for a newly registered account. */
    public void prepare(Account account, String email, String address) {
        idDocument = null;
        byId("id-email").value(orDefault(account.email(), email));
        byId("id-address").value(orDefault(account.address(), address));
        byId("id-type-select").value("");
        byId("id-number").value("");
        byId("id-preview-img").removeAttr("src").display("none");
        byId("id-upload-prompt").display("block");
    }

    private void pickIdPhoto() {
        images().choose("Choose a photo or scan of your ID", ImagePicker.DOCUMENT_TYPES).ifPresent(file -> {
            idDocument = file;
            byId("id-preview-img").src(file.dataUrl()).display("block");
            byId("id-upload-prompt").display("none");
        });
    }

    private void submit() {
        if (!session().isSignedIn()) {
            dialogs().info("Please create an account or log in before submitting ID verification.");
            navigator().navigate("register");
            return;
        }
        if (idDocument == null) {
            dialogs().info("Please upload a picture or scan of your ID before submitting.");
            return;
        }
        ProfileApi.Verification verification = new ProfileApi.Verification(
                byId("id-type-select").value(),
                byId("id-number").trimmedValue(),
                byId("id-address").trimmedValue(),
                byId("id-email").trimmedValue(),
                idDocument.dataUrl());

        UiCall.of(api().profile().submitVerification(verification))
                .onSuccess(ignored -> {
                    session().update(session().require().withVerification(verification.idType(),
                            verification.idNumber(), verification.address(), verification.email()));
                    navigator().navigate("pending");
                })
                .onError(error -> dialogs().error("Could not submit ID verification: "
                        + AuthController.message(error, "Please check your details and try again.")))
                .start();
    }
}
