package io.kestra.core.validations;

import java.io.Serial;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces validation rules upon the application configuration.
 */
@Slf4j
@Context
public class AppConfigValidator {
    private static final String KESTRA_URL_KEY = "kestra.url";

    private final Environment environment;

    @Inject
    public AppConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        final List<ConfigValidationResult> results = validateConfiguration(environment);

        results.stream()
            .filter(result -> !result.valid())
            .forEach(result -> log.error(result.message()));

        if (results.stream().anyMatch(result -> !result.valid())) {
            throw new AppConfigException("Invalid configuration");
        }
    }

    /**
     * Validates the application-wide configuration and returns the outcome of each check.
     *
     * <p>
     * This method is side-effect free (it neither logs nor throws) so the same checks can be
     * reused for on-demand validation.
     *
     * @param environment the configuration environment to validate
     * @return the outcome of each check, never {@code null}
     */
    public static List<ConfigValidationResult> validateConfiguration(final Environment environment) {
        return List.of(
            validateKestraUrl(environment)
        );
    }

    private static ConfigValidationResult validateKestraUrl(final Environment environment) {
        if (!environment.containsProperty(KESTRA_URL_KEY)) {
            return ConfigValidationResult.valid(KESTRA_URL_KEY);
        }
        final String rawUrl = environment.getProperty(KESTRA_URL_KEY, String.class).orElseThrow();
        final URL url;

        try {
            url = URI.create(rawUrl).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            return ConfigValidationResult.invalid(
                KESTRA_URL_KEY,
                "Value of the '" + KESTRA_URL_KEY + "' configuration property must be a valid URL - e.g. https://your.company.com"
            );
        }

        if (!List.of("http", "https").contains(url.getProtocol())) {
            return ConfigValidationResult.invalid(
                KESTRA_URL_KEY,
                "Value of the '" + KESTRA_URL_KEY + "' configuration property must contain either HTTP or HTTPS scheme - e.g. https://your.company.com"
            );
        }

        return ConfigValidationResult.valid(KESTRA_URL_KEY);
    }

    public static class AppConfigException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public AppConfigException(String errorMessage) {
            super(errorMessage);
        }
    }
}
