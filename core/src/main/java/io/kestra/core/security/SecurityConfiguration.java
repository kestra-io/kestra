package io.kestra.core.security;

import java.util.Objects;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for security-related guards applied to user-supplied input.
 */
@ConfigurationProperties("kestra.security")
public record SecurityConfiguration(
    @Nullable ZipBombProtectionConfiguration zipBombProtection
) {

    /**
     * Configuration for the ZIP-bomb protection enforced by {@link ProtectedZipInputStream}
     * when decompressing a user-uploaded ZIP archive.
     *
     * @param enabled whether the protection is active. Defaults to {@code false}.
     * @param maxNumberOfEntries the maximum number of entries allowed in the archive.
     * @param maxEntrySize the maximum uncompressed size, in bytes, allowed for a single entry.
     */
    @ConfigurationProperties("zip-bomb-protection")
    public record ZipBombProtectionConfiguration(
        @Bindable(defaultValue = "false") Boolean enabled,
        @Nullable Integer maxNumberOfEntries,
        @Nullable Integer maxEntrySize
    ) {
        public ZipBombProtectionConfiguration {
            if (Boolean.TRUE.equals(enabled)) {
                Objects.requireNonNull(maxNumberOfEntries, "maxNumberOfEntries");
                Objects.requireNonNull(maxEntrySize, "maxEntrySize");
            }
        }
    }
}
