package io.kestra.core.validations.validator;

import io.kestra.core.validations.TimezoneId;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.time.DateTimeException;
import java.time.ZoneId;

@Singleton
@Introspected
public class TimezoneIdValidator implements ConstraintValidator<TimezoneId, String> {
    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<TimezoneId> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            ZoneId.of(value);
        } catch (DateTimeException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("timezone '({validatedValue})' is not a valid time-zone ID")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
