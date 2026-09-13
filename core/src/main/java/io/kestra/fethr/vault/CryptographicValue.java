package io.kestra.fethr.vault;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * A stored secret value -- a secret's value, a credential's client secret, a password.
 *
 * <p>
 * Kept in its own type rather than a bare {@code String} so plaintext never sits in a field that
 * could be logged or serialized by accident, and so the intent is visible at every use site.
 *
 * <p>
 * Always encrypted at rest: it serializes to a bare ciphertext string, never to an object with a
 * readable field. That is both the security posture and the on-disk shape written before 2.0, so
 * rows already in the database keep decrypting.
 */
@Introspected
@Builder
@JsonSerialize(using = CryptographicValueSerializer.class)
@JsonDeserialize(using = CryptographicValueDeserializer.class)
public record CryptographicValue(@NotBlank String content) {
}
