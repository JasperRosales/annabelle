package com.talkcare;

import java.awt.Desktop;
import java.net.URI;

public class App {
    private static StaticUiServer uiServer;

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        System.out.println("=================================================");
        System.out.println("Starting TalkCare Desktop Application...");
        System.out.println("API:      " + config.apiBaseUrl());
        System.out.println("Local UI: http://localhost:" + config.uiPort() + "/");
        System.out.println("=================================================");

        try {
            uiServer = new StaticUiServer(config);
            uiServer.start();
        } catch (Exception e) {
            System.err.println("Fatal: could not serve the TalkCare UI on port " + config.uiPort() + ": " + e.getMessage());
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(App::stopUiServer));

        try {
            TalkCareFXApp.setStartUrl(uiServer.indexUrl());
            TalkCareFXApp.setOnShutdown(App::stopUiServer);
            TalkCareFXApp.launchApp(args);
        } catch (Throwable t) {
            System.err.println("JavaFX runtime notice: " + t.getMessage());
            System.out.println("Opening TalkCare in the system browser at " + uiServer.indexUrl());
            openBrowserFallback(uiServer.indexUrl());
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void stopUiServer() {
        if (uiServer != null) uiServer.stop();
    }

    private static void openBrowserFallback(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            System.err.println("Could not open desktop browser: " + ex.getMessage());
        }
    }
}
