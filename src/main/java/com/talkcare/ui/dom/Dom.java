package com.talkcare.ui.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** The loaded TalkCare page: element lookup and creation. Must only be used on the JavaFX thread. */
public final class Dom {
    private final Document document;

    public Dom(Document document) {
        this.document = document;
    }

    public Document document() {
        return document;
    }

    /** The element with this id; a missing id is a bug in the HTML/Java pairing, so it fails loudly. */
    public El byId(String id) {
        return findById(id).orElseThrow(() -> new IllegalStateException("No element with id \"" + id + "\""));
    }

    public Optional<El> findById(String id) {
        Element element = document.getElementById(id);
        return element == null ? Optional.empty() : Optional.of(new El(element));
    }

    public El body() {
        return new El((Element) document.getElementsByTagName("body").item(0));
    }

    public El root() {
        return new El(document.getDocumentElement());
    }

    public List<El> byClass(String className) {
        return root().allByClass(className);
    }

    public List<El> all(Predicate<El> filter) {
        return root().descendants(filter);
    }

    public El wrap(Element element) {
        return new El(element);
    }

    public El create(String tag) {
        return new El(document.createElement(tag));
    }

    public El create(String tag, String className) {
        return create(tag).attr("class", className);
    }

    public El create(String tag, String className, String text) {
        return create(tag, className).text(text);
    }

    public void scrollToTop() {
        root().scrollToTop();
        body().scrollToTop();
    }
}
