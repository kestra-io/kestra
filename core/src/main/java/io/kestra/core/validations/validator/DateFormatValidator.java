package io.kestra.core.validations.validator;

import io.kestra.core.validations.DateFormat;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.text.SimpleDateFormat;
import java.util.Date;

@Singleton
@Introspected
public class DateFormatValidator implements ConstraintValidator<DateFormat, String> {
    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<DateFormat> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        try {
            Date now = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat(value);
            dateFormat.format(now);
        } catch (Exception e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("invalid date format value '({validatedValue})': " + e.getMessage())
                .addConstraintViolation();

            return false;
        }
        return true;
    }
}
