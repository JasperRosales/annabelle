package com.talkcare.ui.dom;

import netscape.javascript.JSObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLFormElement;
import org.w3c.dom.html.HTMLInputElement;
import org.w3c.dom.html.HTMLSelectElement;
import org.w3c.dom.html.HTMLTextAreaElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A small fluent wrapper over a WebView DOM element (org.w3c.dom), so UI code reads like
 * {@code dom.byId("login-status").text("...")}. Must only be used on the JavaFX thread.
 */
public final class El {
    private final Element element;

    El(Element element) {
        this.element = Objects.requireNonNull(element);
    }

    public Element raw() {
        return element;
    }

    // ---- text & form values -------------------------------------------------------------

    public El text(String text) {
        element.setTextContent(text == null ? "" : text);
        return this;
    }

    public String text() {
        String text = element.getTextContent();
        return text == null ? "" : text;
    }

    /** Current value of an input, textarea or select (what the user typed or picked). */
    public String value() {
        String value;
        if (element instanceof HTMLInputElement input) value = input.getValue();
        else if (element instanceof HTMLTextAreaElement area) value = area.getValue();
        else if (element instanceof HTMLSelectElement select) value = select.getValue();
        else value = element.getAttribute("value");
        return value == null ? "" : value;
    }

    public String trimmedValue() {
        return value().trim();
    }

    public El value(String value) {
        String v = value == null ? "" : value;
        if (element instanceof HTMLInputElement input) input.setValue(v);
        else if (element instanceof HTMLTextAreaElement area) area.setValue(v);
        else if (element instanceof HTMLSelectElement select) select.setValue(v);
        else element.setAttribute("value", v);
        return this;
    }

    public void focus() {
        if (element instanceof HTMLInputElement input) input.focus();
        else if (element instanceof HTMLTextAreaElement area) area.focus();
    }

    /** Resets a form's fields to their initial values. */
    public void resetForm() {
        if (element instanceof HTMLFormElement form) form.reset();
    }

    // ---- attributes, visibility & state -------------------------------------------------

    public String attr(String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : "";
    }

    public El attr(String name, String value) {
        element.setAttribute(name, value == null ? "" : value);
        return this;
    }

    public El removeAttr(String name) {
        element.removeAttribute(name);
        return this;
    }

    public boolean hasAttr(String name) {
        return element.hasAttribute(name);
    }

    /** The value of a {@code data-*} attribute, or "" when absent. */
    public String data(String name) {
        return attr("data-" + name);
    }

    public El src(String url) {
        return attr("src", url);
    }

    public El disabled(boolean disabled) {
        return disabled ? attr("disabled", "") : removeAttr("disabled");
    }

    /** The HTML {@code hidden} attribute. */
    public El hidden(boolean hidden) {
        return hidden ? attr("hidden", "") : removeAttr("hidden");
    }

    public boolean isHidden() {
        return element.hasAttribute("hidden");
    }

    /** Sets (or with {@code null} clears) the inline CSS {@code display} value, keeping other inline styles. */
    public El display(String value) {
        List<String> declarations = Arrays.stream(attr("style").split(";"))
                .map(String::trim)
                .filter(d -> !d.isEmpty() && !d.toLowerCase().startsWith("display"))
                .collect(Collectors.toCollection(ArrayList::new));
        if (value != null) declarations.add("display: " + value);
        return declarations.isEmpty() ? removeAttr("style") : attr("style", String.join("; ", declarations));
    }

    // ---- CSS classes --------------------------------------------------------------------

    private Set<String> classes() {
        return Arrays.stream(attr("class").trim().split("\\s+"))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasClass(String name) {
        return classes().contains(name);
    }

    public El toggleClass(String name, boolean on) {
        Set<String> classes = classes();
        boolean changed = on ? classes.add(name) : classes.remove(name);
        if (changed) attr("class", String.join(" ", classes));
        return this;
    }

    public El addClass(String name) {
        return toggleClass(name, true);
    }

    public El removeClass(String name) {
        return toggleClass(name, false);
    }

    /** Flips a class and returns whether it is now present. */
    public boolean toggleClass(String name) {
        boolean on = !hasClass(name);
        toggleClass(name, on);
        return on;
    }

    // ---- tree ---------------------------------------------------------------------------

    public El append(El... children) {
        for (El child : children) element.appendChild(child.element);
        return this;
    }

    public El appendText(String text) {
        element.appendChild(element.getOwnerDocument().createTextNode(text == null ? "" : text));
        return this;
    }

    public El clear() {
        while (element.getFirstChild() != null) element.removeChild(element.getFirstChild());
        return this;
    }

    public El replaceChildren(El... children) {
        return clear().append(children);
    }

    public boolean contains(Node node) {
        for (Node n = node; n != null; n = n.getParentNode()) {
            if (n == element || element.isSameNode(n)) return true;
        }
        return false;
    }

    /** All descendants matching a predicate, in document order. */
    public List<El> descendants(Predicate<El> filter) {
        List<El> result = new ArrayList<>();
        NodeList all = element.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element e) {
                El el = new El(e);
                if (filter.test(el)) result.add(el);
            }
        }
        return result;
    }

    public List<El> allByClass(String className) {
        return descendants(el -> el.hasClass(className));
    }

    public El firstByClass(String className) {
        return allByClass(className).stream().findFirst().orElseThrow(() ->
                new IllegalStateException("No ." + className + " inside #" + attr("id")));
    }

    public Optional<El> closest(Predicate<El> filter) {
        for (Node n = element; n != null; n = n.getParentNode()) {
            if (n instanceof Element e) {
                El el = new El(e);
                if (filter.test(el)) return Optional.of(el);
            }
        }
        return Optional.empty();
    }

    // ---- scrolling ----------------------------------------------------------------------

    @SuppressWarnings("removal") // JSObject now ships with JavaFX (org.openjfx:jdk-jsobject)
    public void scrollToBottom() {
        if (element instanceof JSObject js) js.setMember("scrollTop", js.getMember("scrollHeight"));
    }

    @SuppressWarnings("removal")
    public void scrollToTop() {
        if (element instanceof JSObject js) js.setMember("scrollTop", 0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof El other && (other.element == element || element.isSameNode(other.element));
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(element);
    }
}
