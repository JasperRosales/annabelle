package com.talkcare;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.InputStream;

public class TalkCareFXApp extends Application {
    private static String startUrl = "http://localhost:54321/index.html";
    private static Runnable onShutdown;

    public static void setStartUrl(String url) {
        startUrl = url;
    }

    public static void setOnShutdown(Runnable r) {
        onShutdown = r;
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();
            webEngine.setJavaScriptEnabled(true);

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    primaryStage.setTitle("TalkCare - Mental Health & Justice Platform");
                }
            });

            webEngine.load(startUrl);

            StackPane root = new StackPane(webView);
            Scene scene = new Scene(root, 1366, 768);

            primaryStage.setTitle("TalkCare - Mental Health & Justice Platform");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(650);

            try (InputStream iconStream = getClass().getClassLoader().getResourceAsStream("static/assets/talkcare-logo.png")) {
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception ignored) {}

            primaryStage.setOnCloseRequest(e -> {
                if (onShutdown != null) {
                    onShutdown.run();
                }
                Platform.exit();
                System.exit(0);
            });

            primaryStage.show();
        } catch (Throwable t) {
            System.err.println("Could not launch JavaFX stage: " + t.getMessage());
            throw new RuntimeException(t);
        }
    }

    public static void launchApp(String[] args) {
        launch(TalkCareFXApp.class, args);
    }
}
