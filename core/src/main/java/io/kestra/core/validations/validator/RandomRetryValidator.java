package io.kestra.core.validations.validator;

import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.validations.RandomRetryValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class RandomRetryValidator implements ConstraintValidator<RandomRetryValidation, Random> {
    @Override
    public boolean isValid(@Nullable Random value, @NonNull AnnotationValue<RandomRetryValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getMaxDuration() != null && value.getMaxInterval() != null && value.getMaxDuration().compareTo(value.getMinInterval()) <= 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate( "'minInterval' must be less than 'maxDuration' but is " + value.getMinInterval())
                .addConstraintViolation();
            return false;
        }

        if (value.getMaxDuration() != null && value.getMaxInterval() != null && value.getMaxDuration().compareTo(value.getMaxInterval()) <= 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate( "'maxInterval' must be less than 'maxDuration' but is " + value.getMaxInterval())
                .addConstraintViolation();
            return false;
        }

        if (value.getMaxInterval() != null && value.getMinInterval() != null && value.getMaxInterval().compareTo(value.getMinInterval()) <= 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate( "'minInterval' must be less than 'maxInterval' but is " + value.getMinInterval())
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
