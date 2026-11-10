package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A signed-in TalkCare account as returned by /api/auth/login, /api/auth/register,
 * PATCH /api/profile and /api/admin/users. The API decides the role and the home view.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Account(
        long id,
        String username,
        String email,
        String fullName,
        String contactNo,
        String address,
        String profilePictureUrl,
        String role,
        String roleDisplayName,
        String status,
        String dashboardView,
        List<String> permissions,
        String idType,
        String idNumber,
        String sessionToken) {

    public static final String ROLE_CLIENT = "CLIENT";
    public static final String ROLE_ATTORNEY = "ATTORNEY";
    public static final String ROLE_PSYCHIATRIST = "PSYCHIATRIST";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_PENDING = "PENDING_VERIFICATION";

    public String displayName() {
        return isBlank(fullName) ? username : fullName;
    }

    public String roleLabel() {
        if (!isBlank(roleDisplayName)) return roleDisplayName;
        return isBlank(role) ? "Client" : role;
    }

    public boolean isClient() {
        return ROLE_CLIENT.equals(role);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** Chat, Book a Session and Report a Concern stay hidden from clients until they are verified. */
    public boolean canUseServices() {
        return !isClient() || isVerified();
    }

    public boolean isProfessional() {
        return ROLE_ATTORNEY.equals(role) || ROLE_PSYCHIATRIST.equals(role);
    }

    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    /** An ID was submitted and is waiting for an administrator. */
    public boolean isAwaitingReview() {
        return STATUS_PENDING.equals(status) && !isBlank(idType);
    }

    /**
     * The view the API assigns to this account (Account.dashboardView on the server). Clients
     * land on their dashboard; older API builds still send the removed "home" view for them.
     */
    public String homeView() {
        return isBlank(dashboardView) || "home".equals(dashboardView) ? "dashboard" : dashboardView;
    }

    public Account withProfilePicture(String url) {
        return new Account(id, username, email, fullName, contactNo, address, url, role, roleDisplayName,
                status, dashboardView, permissions, idType, idNumber, sessionToken);
    }

    public Account withSessionToken(String token) {
        return new Account(id, username, email, fullName, contactNo, address, profilePictureUrl, role,
                roleDisplayName, status, dashboardView, permissions, idType, idNumber, token);
    }

    public Account withVerification(String newIdType, String newIdNumber, String newAddress, String newEmail) {
        return new Account(id, username, newEmail, fullName, contactNo, newAddress, profilePictureUrl, role,
                roleDisplayName, STATUS_PENDING, dashboardView, permissions, newIdType, newIdNumber,
                sessionToken);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
