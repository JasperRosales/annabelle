package com.talkcare.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.util.Optional;

/** Native JavaFX message boxes (replacing the browser's alert/prompt). */
public final class Dialogs {
    private static final String TITLE = "TalkCare";
    private final Window owner;

    public Dialogs(Window owner) {
        this.owner = owner;
    }

    public void info(String message) {
        show(Alert.AlertType.INFORMATION, message);
    }

    public void error(String message) {
        show(Alert.AlertType.ERROR, message);
    }

    public Optional<String> prompt(String message) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle(TITLE);
        dialog.setHeaderText(null);
        dialog.setContentText(message);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private void show(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(TITLE);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
