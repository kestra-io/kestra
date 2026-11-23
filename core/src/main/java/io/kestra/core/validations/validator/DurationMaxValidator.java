package io.kestra.core.validations.validator;

import java.time.Duration;

import io.kestra.core.validations.DurationMax;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class DurationMaxValidator implements ConstraintValidator<DurationMax, Duration> {
    @Override
    public boolean isValid(
        @Nullable Duration value,
        @NonNull AnnotationValue<DurationMax> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String maxValue = annotationMetadata.stringValue("max").orElse("P3650D");
        Duration max = Duration.parse(maxValue);

        if (value.compareTo(max) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("duration '" + value + "' must not exceed " + maxValue)
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
