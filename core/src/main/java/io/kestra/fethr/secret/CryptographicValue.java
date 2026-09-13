package io.kestra.fethr.secret;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * The value half of a {@link Secret}, kept in its own type so a secret's plaintext never sits in a
 * bare {@code String} field that could be logged or serialized by accident.
 */
@Introspected
@Builder
public record CryptographicValue(@NotBlank String content) {
}
