package com.talkcare.ui;

import com.talkcare.api.ApiClient;
import com.talkcare.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Who is signed in. Observers (e.g. the navbar) are told whenever the account changes. */
public final class Session {
    private final ApiClient client;
    private final List<Runnable> listeners = new ArrayList<>();
    private Account account;

    public Session(ApiClient client) {
        this.client = client;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void signIn(Account signedIn) {
        account = signedIn;
        client.setSessionToken(signedIn.sessionToken());
        notifyListeners();
    }

    /** Replaces the account details (profile edits, verification) but keeps the session token. */
    public void update(Account updated) {
        if (account == null) return;
        account = updated.sessionToken() == null ? updated.withSessionToken(account.sessionToken()) : updated;
        notifyListeners();
    }

    public void signOut() {
        account = null;
        client.setSessionToken(null);
        notifyListeners();
    }

    public boolean isSignedIn() {
        return account != null;
    }

    public Optional<Account> account() {
        return Optional.ofNullable(account);
    }

    /** The signed-in account; only call where a session is guaranteed. */
    public Account require() {
        if (account == null) throw new IllegalStateException("Not signed in");
        return account;
    }

    public boolean isProfessional() {
        return account != null && account.isProfessional();
    }

    public String homeView() {
        return account == null ? "welcome" : account.homeView();
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
