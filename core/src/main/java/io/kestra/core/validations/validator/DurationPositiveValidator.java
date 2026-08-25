package io.kestra.core.validations.validator;

import java.time.Duration;

import io.kestra.core.validations.DurationPositive;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class DurationPositiveValidator implements ConstraintValidator<DurationPositive, Duration> {
    @Override
    public boolean isValid(
        @Nullable Duration value,
        @NonNull AnnotationValue<DurationPositive> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.isNegative() || value.isZero()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("duration '" + value + "' must be positive")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
