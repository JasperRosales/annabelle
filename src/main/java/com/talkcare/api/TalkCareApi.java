package com.talkcare.api;

/**
 * Entry point to the TalkCare REST API, grouped by area. Each area is a small service object
 * that knows its endpoints and payloads; they share one {@link ApiClient} (and session token).
 */
public final class TalkCareApi {
    private final ApiClient client;
    private final AuthApi auth;
    private final ProfileApi profile;
    private final ChatApi chat;
    private final ChatbotApi chatbot;
    private final ReportApi reports;
    private final AdminApi admin;
    private final HealthApi health;
    private final AppointmentApi appointments;
    private final NotificationApi notifications;

    public TalkCareApi(String baseUrl) {
        this.client = new ApiClient(baseUrl);
        this.auth = new AuthApi(client);
        this.profile = new ProfileApi(client);
        this.chat = new ChatApi(client);
        this.chatbot = new ChatbotApi(client);
        this.reports = new ReportApi(client);
        this.admin = new AdminApi(client);
        this.health = new HealthApi(client);
        this.appointments = new AppointmentApi(client);
        this.notifications = new NotificationApi(client);
    }

    public ApiClient client() { return client; }
    public AuthApi auth() { return auth; }
    public ProfileApi profile() { return profile; }
    public ChatApi chat() { return chat; }
    public ChatbotApi chatbot() { return chatbot; }
    public ReportApi reports() { return reports; }
    public AdminApi admin() { return admin; }
    public HealthApi health() { return health; }
    public AppointmentApi appointments() { return appointments; }
    public NotificationApi notifications() { return notifications; }
}
