package io.kestra.core.validations.validator;

import java.time.Duration;

import io.kestra.core.validations.DurationMin;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class DurationMinValidator implements ConstraintValidator<DurationMin, Duration> {
    @Override
    public boolean isValid(
        @Nullable Duration value,
        @NonNull AnnotationValue<DurationMin> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // Not merely defensive: validation delegated to Hibernate passes a bare @Constraint with no
        // members, so this literal is the only bound that path ever sees. Keep it equal to min()'s default.
        String minValue = annotationMetadata.stringValue("min").orElse("PT0.001S");
        Duration min = Duration.parse(minValue);

        if (value.compareTo(min) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("duration '" + value + "' must be at least " + minValue)
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
