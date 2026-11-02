package com.talkcare;

import com.talkcare.server.StaticUiServer;

/** Starts the local UI server and the JavaFX window. */
public class App {

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        System.out.println("=================================================");
        System.out.println("Starting TalkCare Desktop Application...");
        System.out.println("API:      " + config.apiBaseUrl());
        System.out.println("Local UI: http://localhost:" + config.uiPort() + "/");
        System.out.println("=================================================");

        StaticUiServer uiServer = new StaticUiServer(config.uiPort());
        try {
            uiServer.start();
        } catch (Exception e) {
            System.err.println("Fatal: could not serve the TalkCare UI on port " + config.uiPort() + ": " + e.getMessage());
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(uiServer::stop));

        TalkCareFXApp.configure(config, uiServer);
        TalkCareFXApp.launchApp(args);
    }
}
