package com.talkcare.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/** Repeats a task on the JavaFX thread (used to refresh chat messages every few seconds). */
public final class Poller {
    private final Duration interval;
    private Timeline timeline;

    public Poller(Duration interval) {
        this.interval = interval;
    }

    public void start(Runnable task) {
        stop();
        timeline = new Timeline(new KeyFrame(interval, e -> task.run()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
