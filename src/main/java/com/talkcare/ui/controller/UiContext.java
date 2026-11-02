package com.talkcare.ui.controller;

import com.talkcare.api.TalkCareApi;
import com.talkcare.ui.Dialogs;
import com.talkcare.ui.ImagePicker;
import com.talkcare.ui.Navigator;
import com.talkcare.ui.Session;
import com.talkcare.ui.dom.Dom;

/** The shared services every controller works with. */
public record UiContext(Dom dom, TalkCareApi api, Session session, Navigator navigator,
                        Dialogs dialogs, ImagePicker images) {}
