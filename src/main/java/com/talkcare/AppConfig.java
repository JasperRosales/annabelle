package com.talkcare;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Desktop settings: where the API lives and which local port serves the UI. */
public final class AppConfig {
    private final String apiBaseUrl;
    private final int uiPort;

    private AppConfig(String apiBaseUrl, int uiPort) {
        this.apiBaseUrl = apiBaseUrl;
        this.uiPort = uiPort;
    }

    /** Precedence: system property, then environment variable, then config.properties. */
    public static AppConfig load() {
        Properties file = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) file.load(in);
        } catch (IOException e) {
            System.err.println("Could not read config.properties: " + e.getMessage());
        }
        String api = firstNonBlank(System.getProperty("talkcare.api.url"), System.getenv("TALKCARE_API_URL"),
                file.getProperty("api.baseUrl"), "https://talkcare-api.onrender.com");
        String port = firstNonBlank(System.getProperty("talkcare.ui.port"), System.getenv("TALKCARE_UI_PORT"),
                file.getProperty("ui.port"), "54321");
        return new AppConfig(api.replaceAll("/+$", ""), Integer.parseInt(port.trim()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    public String apiBaseUrl() { return apiBaseUrl; }
    public int uiPort() { return uiPort; }
}
