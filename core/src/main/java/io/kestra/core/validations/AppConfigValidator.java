package io.kestra.core.validations;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

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
        final List<Boolean> validationResults = List.of(
            isKestraUrlValid()
        );

        if (validationResults.contains(false)) {
            throw new AppConfigException("Invalid configuration");
        }
    }

    private boolean isKestraUrlValid() {
        if (!environment.containsProperty(KESTRA_URL_KEY)) {
            return true;
        }
        final String rawUrl = environment.getProperty(KESTRA_URL_KEY, String.class).orElseThrow();
        final URL url;

        try {
            url = URI.create(rawUrl).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            log.error(
                "Value of the '{}' configuration property must be a valid URL - e.g. https://your.company.com",
                KESTRA_URL_KEY
            );
            return false;
        }

        if (!List.of("http", "https").contains(url.getProtocol())) {
            log.error(
                "Value of the '{}' configuration property must contain either HTTP or HTTPS scheme - e.g. https://your.company.com",
                KESTRA_URL_KEY
            );
            return false;
        }

        return true;
    }

    public static class AppConfigException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public AppConfigException(String errorMessage) {
            super(errorMessage);
        }
    }
}