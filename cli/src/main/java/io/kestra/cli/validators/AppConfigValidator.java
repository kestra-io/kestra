package io.kestra.cli.validators;

import io.kestra.cli.AppConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;

@Singleton
public class AppConfigValidator {
    @Valid
    private final AppConfig config;

    @Inject
    public AppConfigValidator(AppConfig config) {
        this.config = config;
    }
}
