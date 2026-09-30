package io.kestra.webserver.services;

public record BasicAuthCredentials(
    String uid,
    String username,
    String password,
    String currentPassword) {
    public BasicAuthCredentials(String uid, String username, String password) {
        this(uid, username, password, null);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUid() {
        return uid;
    }

    /**
     * The password the caller must currently know, required to change already-configured
     * Basic Authentication credentials. See {@link BasicAuthService#validateCurrentPassword}.
     */
    public String getCurrentPassword() {
        return currentPassword;
    }
}
