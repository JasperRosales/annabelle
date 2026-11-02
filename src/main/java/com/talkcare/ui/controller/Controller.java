package com.talkcare.ui.controller;

import com.talkcare.api.TalkCareApi;
import com.talkcare.ui.ActionRouter;
import com.talkcare.ui.Dialogs;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.Navigator;
import com.talkcare.ui.Session;
import com.talkcare.ui.dom.Dom;
import com.talkcare.ui.dom.El;

/**
 * Base class for the page controllers. Each controller owns one part of the page, registers
 * the {@code data-action}/{@code data-submit} names it handles in {@link #bind}, and talks to
 * the API through {@link #api()}.
 */
public abstract class Controller {
    private final UiContext context;

    protected Controller(UiContext context) {
        this.context = context;
    }

    /** Registers this controller's handlers. */
    public abstract void bind(ActionRouter router);

    protected Dom dom() { return context.dom(); }
    protected TalkCareApi api() { return context.api(); }
    protected Session session() { return context.session(); }
    protected Navigator navigator() { return context.navigator(); }
    protected Dialogs dialogs() { return context.dialogs(); }
    protected ImagePicker images() { return context.images(); }

    protected El byId(String id) {
        return context.dom().byId(id);
    }

    /** API upload paths ("/uploads/...") point at the server; bundled assets stay local. */
    protected String assetUrl(String url) {
        return context.api().client().resolveAssetUrl(url);
    }

    /** The first submit button inside a form. */
    protected static El submitButton(El form) {
        return form.descendants(el -> "submit".equalsIgnoreCase(el.attr("type"))
                && "BUTTON".equalsIgnoreCase(el.raw().getTagName())).getFirst();
    }

    protected static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
