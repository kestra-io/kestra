package io.kestra.core.services;

import java.util.Optional;

import jakarta.inject.Singleton;

/**
 * Resolves the identity of the user making the current request.
 * <p>
 * OSS has no user model: every request behind OSS's single-admin basic-auth is the same implicit
 * identity, {@value #DEFAULT_USER_ID}. Extended and {@code @Replaces}d where a real per-request
 * identity exists (e.g. an EE edition with its own user/RBAC model).
 */
@Singleton
public class CurrentUserProvider {
    public static final String DEFAULT_USER_ID = "default";

    public Optional<String> currentUserId() {
        return Optional.of(DEFAULT_USER_ID);
    }
}
