package io.kestra.core.contexts.configuration;

/**
 * A configuration property that was valid in a previous major version and is no longer honoured.
 *
 * @param key         the legacy property key, or the prefix of a legacy configuration block.
 * @param replacement the key that replaces it, or {@code null} when the property was removed outright.
 * @param severity    whether keeping the property only warrants a warning, or must fail the startup.
 */
public record LegacyConfiguration(String key, String replacement, Severity severity) {

    public enum Severity {
        WARN,
        ERROR
    }

    public LegacyConfiguration {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("The legacy configuration key cannot be null or blank.");
        }
    }

    public static LegacyConfiguration removed(final String key, final Severity severity) {
        return new LegacyConfiguration(key, null, severity);
    }

    public static LegacyConfiguration renamed(final String key, final String replacement, final Severity severity) {
        if (replacement == null || replacement.isBlank()) {
            throw new IllegalArgumentException("The replacement of the legacy configuration key '%s' cannot be null or blank.".formatted(key));
        }

        return new LegacyConfiguration(key, replacement, severity);
    }

    /**
     * @return a human-readable description of the property and of what must be done with it.
     */
    public String describe() {
        return replacement == null
            ? "`%s` has been removed.".formatted(key)
            : "`%s` has been renamed to `%s`.".formatted(key, replacement);
    }
}
