package io.kestra.core.validations;

/**
 * Outcome of a single configuration validation check.
 *
 * <p>
 * Instances are produced by the configuration validators (e.g. {@link AppConfigValidator},
 * {@link ServerCommandValidator}) so that the same checks can be reused both at boot time and
 * for on-demand validation (e.g. the {@code configs validate} CLI command).
 *
 * @param key the configuration property (or logical check name) that was validated
 * @param valid whether the check passed
 * @param message a human-readable explanation of the failure, {@code null} when the check passed
 */
public record ConfigValidationResult(String key, boolean valid, String message) {
    public static ConfigValidationResult valid(final String key) {
        return new ConfigValidationResult(key, true, null);
    }

    public static ConfigValidationResult invalid(final String key, final String message) {
        return new ConfigValidationResult(key, false, message);
    }
}
