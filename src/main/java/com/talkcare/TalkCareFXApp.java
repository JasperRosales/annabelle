package com.talkcare;

import com.talkcare.api.TalkCareApi;
import com.talkcare.server.StaticUiServer;
import com.talkcare.ui.TalkCareUi;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * The desktop window: a WebView showing the TalkCare HTML/CSS, driven entirely by Java
 * ({@link TalkCareUi}). The page contains no JavaScript.
 */
public class TalkCareFXApp extends Application {
    private static final String TITLE = "TalkCare - Mental Health & Justice Platform";

    private static AppConfig config;
    private static StaticUiServer uiServer;

    private TalkCareUi ui;
    private String pendingResetToken;

    /** Must be called before {@link #launchApp}. */
    public static void configure(AppConfig appConfig, StaticUiServer server) {
        config = appConfig;
        uiServer = server;
    }

    public static void launchApp(String[] args) {
        launch(TalkCareFXApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        WebEngine engine = webView.getEngine();
        TalkCareApi api = new TalkCareApi(config.apiBaseUrl());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, state) -> {
            if (state == Worker.State.SUCCEEDED && ui == null) {
                ui = new TalkCareUi(engine.getDocument(), stage, api);
                ui.start();
                if (pendingResetToken != null) ui.openPasswordReset(pendingResetToken);
            } else if (state == Worker.State.FAILED) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "TalkCare could not load its screens. Please restart the app.");
                alert.initOwner(stage);
                alert.showAndWait();
            }
        });

        uiServer.onResetToken(token -> Platform.runLater(() -> {
            if (ui == null) pendingResetToken = token;
            else ui.openPasswordReset(token);
            stage.toFront();
            stage.requestFocus();
        }));

        engine.load(uiServer.indexUrl());

        stage.setTitle(TITLE);
        stage.setScene(new Scene(new StackPane(webView), 1366, 768));
        stage.setMinWidth(1024);
        stage.setMinHeight(650);
        try (InputStream icon = getClass().getClassLoader().getResourceAsStream("static/assets/talkcare-logo.png")) {
            if (icon != null) stage.getIcons().add(new Image(icon));
        } catch (Exception ignored) {
            // The window works without an icon.
        }
        stage.setOnCloseRequest(e -> {
            uiServer.stop();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }
}
