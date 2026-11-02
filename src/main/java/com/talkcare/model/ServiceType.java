package com.talkcare.model;

import java.util.Optional;

/** The two TalkCare services a client can chat with, and how each is presented in the UI. */
public enum ServiceType {
    JUSTICE_CARE("JusticeCare", "Atty. Sofia Ramos", "Licensed Case Handler & Legal Rights Advocate",
            "assets/seal-justicecare.svg", "assets/avatar-case-handler.svg", "JusticeCare Legal Support",
            "Type your concerns.....", false),
    THOMATT("ThoMatt", "Dr. Eleanor Reyes", "R.Psy / L.P.T. (Licensed Psychologist & Therapist)",
            "assets/seal-thomatt.svg", "assets/avatar-therapist.svg", "ThoMatt Consultation",
            "Tell your thoughts ...", true);

    private final String displayName;
    private final String professionalName;
    private final String professionalTitle;
    private final String seal;
    private final String avatar;
    private final String preview;
    private final String placeholder;
    private final boolean showsCertificate;

    ServiceType(String displayName, String professionalName, String professionalTitle, String seal,
                String avatar, String preview, String placeholder, boolean showsCertificate) {
        this.displayName = displayName;
        this.professionalName = professionalName;
        this.professionalTitle = professionalTitle;
        this.seal = seal;
        this.avatar = avatar;
        this.preview = preview;
        this.placeholder = placeholder;
        this.showsCertificate = showsCertificate;
    }

    public static Optional<ServiceType> fromCode(String code) {
        for (ServiceType type : values()) {
            if (type.name().equals(code)) return Optional.of(type);
        }
        return Optional.empty();
    }

    public String displayName() { return displayName; }
    public String professionalName() { return professionalName; }
    public String professionalTitle() { return professionalTitle; }
    public String seal() { return seal; }
    public String avatar() { return avatar; }
    public String preview() { return preview; }
    public String placeholder() { return placeholder; }
    public boolean showsCertificate() { return showsCertificate; }
}
