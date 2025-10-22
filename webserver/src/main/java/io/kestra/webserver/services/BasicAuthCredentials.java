package io.kestra.webserver.services;


public record BasicAuthCredentials(
    String uid,
    String username,
    String password
) {
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUid() {
        return uid;
    }
}