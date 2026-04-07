package io.kestra.core.encryption;

import java.util.Optional;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Singleton bean holding the application encryption secret key.
 * <p>
 * This is the single source of truth for the system encryption key configured
 * via the {@code kestra.encryption.secret-key} application property.
 */
@Singleton
public class EncryptionConfig {

    public static final String CONFIG_KEY = "${kestra.encryption.secret-key}";

    private final Optional<String> secretKey;

    @Inject
    public EncryptionConfig(@Nullable @Value(CONFIG_KEY) String secretKey) {
        this.secretKey = Optional.ofNullable(secretKey);
    }

    /**
     * Returns the encryption key or throws if not configured.
     *
     * @throws IllegalStateException if no encryption key is configured
     */
    public String get() {
        return secretKey.orElseThrow(
            () -> new IllegalStateException(
                "No encryption key configured through the application configuration property: 'kestra.encryption.secret-key'."
            )
        );
    }

    /** Returns the encryption key as an {@link Optional}. */
    public Optional<String> asOptional() {
        return secretKey;
    }

    /** Returns {@code true} if an encryption key is configured. */
    public boolean isConfigured() {
        return secretKey.isPresent();
    }
}
