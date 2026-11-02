package com.talkcare;

import java.awt.Desktop;
import java.net.URI;

public class App {
    private static final int PORT = 54321;
    private static TalkCareServer server;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Starting TalkCare Desktop Application...");
        System.out.println("Backend: PostgreSQL (Port 5432)");
        System.out.println("Local Server: http://localhost:" + PORT + "/");
        System.out.println("=================================================");

        // Initialize embedded server
        try {
            server = new TalkCareServer(PORT);
            server.start();
        } catch (Exception e) {
            System.err.println("Fatal: Failed to start TalkCare server: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
        }));

        // Try launching JavaFX stage
        try {
            TalkCareFXApp.setPort(PORT);
            TalkCareFXApp.setOnShutdown(() -> {
                if (server != null) {
                    server.stop();
                }
            });
            TalkCareFXApp.launchApp(args);
        } catch (Throwable t) {
            System.err.println("JavaFX runtime notice: " + t.getMessage());
            System.out.println("Launching Desktop window at http://localhost:" + PORT + "/");
            openBrowserFallback();
            try {
                // Keep server thread alive
                Thread.currentThread().join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static void openBrowserFallback() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + PORT + "/index.html"));
            }
        } catch (Exception ex) {
            System.err.println("Could not open desktop browser: " + ex.getMessage());
        }
    }
}
