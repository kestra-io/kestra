package io.kestra.core.validations.validator;

import java.time.Duration;
import java.util.Objects;

import io.kestra.core.contexts.configuration.DashboardsConfiguration;
import io.kestra.core.validations.DashboardQueryTimeoutValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DashboardQueryTimeoutValidator implements ConstraintValidator<DashboardQueryTimeoutValidation, Duration> {
    private final DashboardsConfiguration configuration;

    @Inject
    public DashboardQueryTimeoutValidator(DashboardsConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Override
    public boolean isValid(
        @Nullable Duration value,
        @NonNull AnnotationValue<DashboardQueryTimeoutValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String violation = null;
        if (!value.isPositive()) {
            violation = "Query timeout must be positive.";
        } else if (value.compareTo(configuration.maxQueryTimeout()) > 0) {
            violation = "Query timeout can't exceed the configured maximum of %d seconds.".formatted(configuration.maxQueryTimeout().toSeconds());
        }

        if (violation == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violation).addConstraintViolation();
        return false;
    }
}
