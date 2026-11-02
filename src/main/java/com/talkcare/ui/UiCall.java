package com.talkcare.ui;

import com.talkcare.api.ApiException;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs an API call in the background and delivers its outcome on the JavaFX thread, where the
 * page may be updated:
 * <pre>{@code
 * UiCall.of(api.auth().login(user, pass))
 *       .onSuccess(session::signIn)
 *       .onError(e -> status.text(e.getMessage()))
 *       .always(() -> button.disabled(false))
 *       .start();
 * }</pre>
 */
public final class UiCall<T> {
    private final CompletableFuture<T> future;
    private Consumer<T> onSuccess = value -> {};
    private Consumer<ApiException> onError = error -> System.err.println("TalkCare: " + error.getMessage());
    private Runnable always = () -> {};

    private UiCall(CompletableFuture<T> future) {
        this.future = future;
    }

    public static <T> UiCall<T> of(CompletableFuture<T> future) {
        return new UiCall<>(future);
    }

    public UiCall<T> onSuccess(Consumer<T> handler) {
        this.onSuccess = handler;
        return this;
    }

    public UiCall<T> onError(Consumer<ApiException> handler) {
        this.onError = handler;
        return this;
    }

    public UiCall<T> always(Runnable handler) {
        this.always = handler;
        return this;
    }

    public void start() {
        future.whenComplete((value, error) -> Platform.runLater(() -> {
            try {
                if (error == null) onSuccess.accept(value);
                else onError.accept(toApiException(error));
            } catch (RuntimeException e) {
                System.err.println("TalkCare UI error: " + e);
                e.printStackTrace();
            } finally {
                always.run();
            }
        }));
    }

    static ApiException toApiException(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof ApiException api ? api : new ApiException(String.valueOf(cause.getMessage()), cause);
    }
}
