package com.talkcare.ui;

import com.talkcare.ui.dom.Dom;
import com.talkcare.ui.dom.El;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Connects the HTML to Java. Elements declare what they do with attributes, and controllers
 * register a handler per name:
 * <ul>
 *   <li>{@code data-action="name" data-arg="..."}: click (add {@code data-self} to react only to
 *       clicks on the element itself, e.g. a modal backdrop)</li>
 *   <li>{@code <form data-submit="name">}: form submission (the page never reloads)</li>
 *   <li>{@code data-input="name"}: typing in a field</li>
 * </ul>
 * One listener per event type sits on the document, so elements created later work too.
 */
public final class ActionRouter {
    /** What triggered a handler: the declaring element, its {@code data-arg}, and the DOM event. */
    public record Action(El source, String arg, Event event) {}

    private final Map<String, Consumer<Action>> clicks = new HashMap<>();
    private final Map<String, Consumer<Action>> submits = new HashMap<>();
    private final Map<String, Consumer<Action>> inputs = new HashMap<>();
    private final List<Consumer<Node>> clickObservers = new ArrayList<>();
    private final Consumer<RuntimeException> errorHandler;
    private Dom dom;

    /** WebKit only holds weak references to Java listeners, so they are kept here. */
    private final List<EventListener> listeners = new ArrayList<>();

    public ActionRouter(Consumer<RuntimeException> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ActionRouter onClick(String name, Consumer<Action> handler) {
        register(clicks, name, handler);
        return this;
    }

    public ActionRouter onSubmit(String name, Consumer<Action> handler) {
        register(submits, name, handler);
        return this;
    }

    public ActionRouter onInput(String name, Consumer<Action> handler) {
        register(inputs, name, handler);
        return this;
    }

    /** Sees every click first, with its target node (e.g. to close a menu on outside clicks). */
    public ActionRouter observeClicks(Consumer<Node> observer) {
        clickObservers.add(observer);
        return this;
    }

    public void attach(Dom dom) {
        this.dom = dom;
        listen("click", this::handleClick);
        listen("submit", this::handleSubmit);
        listen("input", this::handleInput);
    }

    private void listen(String type, EventListener listener) {
        listeners.add(listener);
        ((EventTarget) dom.document()).addEventListener(type, listener, false);
    }

    private static void register(Map<String, Consumer<Action>> map, String name, Consumer<Action> handler) {
        if (map.putIfAbsent(name, handler) != null) {
            throw new IllegalStateException("Two handlers registered for \"" + name + "\"");
        }
    }

    private void handleClick(Event event) {
        Element target = targetElement(event);
        if (target == null) return;
        clickObservers.forEach(observer -> safely(() -> observer.accept(target)));

        dom.wrap(target).closest(el -> el.hasAttr("data-action")).ifPresent(source -> {
            if (source.hasAttr("data-self") && !source.raw().isSameNode(target)) return;
            dispatch(clicks, source.data("action"), new Action(source, source.data("arg"), event));
        });
    }

    private void handleSubmit(Event event) {
        Element target = targetElement(event);
        if (target == null || !target.hasAttribute("data-submit")) return;
        event.preventDefault();
        El form = dom.wrap(target);
        dispatch(submits, form.data("submit"), new Action(form, form.data("arg"), event));
    }

    private void handleInput(Event event) {
        Element target = targetElement(event);
        if (target == null || !target.hasAttribute("data-input")) return;
        El field = dom.wrap(target);
        dispatch(inputs, field.data("input"), new Action(field, field.data("arg"), event));
    }

    private void dispatch(Map<String, Consumer<Action>> handlers, String name, Action action) {
        Consumer<Action> handler = handlers.get(name);
        if (handler == null) {
            System.err.println("No Java handler for \"" + name + "\"");
            return;
        }
        safely(() -> handler.accept(action));
    }

    private void safely(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
    }

    private static Element targetElement(Event event) {
        Node node = event.getTarget() instanceof Node n ? n : null;
        while (node != null && !(node instanceof Element)) node = node.getParentNode();
        return (Element) node;
    }
}
