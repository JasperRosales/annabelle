package com.talkcare.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the single TalkCare page: {@code static/index.html} with every
 * {@code <div data-include="views/x.html"></div>} replaced by that file's HTML, so each screen
 * can live in its own file while the WebView still loads one complete document.
 */
public final class PageAssembler {
    private static final Pattern INCLUDE = Pattern.compile("<div data-include=\"([\\w./-]+\\.html)\"></div>");
    private static final int MAX_DEPTH = 5;

    private final String root;
    private volatile String page;

    public PageAssembler(String resourceRoot) {
        this.root = resourceRoot.endsWith("/") ? resourceRoot : resourceRoot + "/";
    }

    /** The assembled page (built once, then cached). */
    public String page() {
        String result = page;
        if (result == null) {
            result = expand(read("index.html"), 0);
            page = result;
        }
        return result;
    }

    private String expand(String html, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalStateException("data-include nested too deeply");
        Matcher matcher = INCLUDE.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.contains("..")) throw new IllegalStateException("Invalid include path: " + path);
            matcher.appendReplacement(out, Matcher.quoteReplacement(expand(read(path), depth + 1)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String read(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(root + path)) {
            if (in == null) throw new IllegalStateException("Missing UI file: " + root + path);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
