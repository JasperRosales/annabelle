package com.talkcare.ui;

import java.util.Base64;

/** An image the user picked from disk, ready to preview or upload as a data URL. */
public record ImageFile(String name, String mimeType, byte[] bytes) {

    public String dataUrl() {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
