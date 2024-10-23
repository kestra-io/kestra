package io.kestra.core.validations.validator;

import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.validations.ConstantRetryValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class ConstantRetryValidator implements ConstraintValidator<ConstantRetryValidation, Constant> {
    @Override
    public boolean isValid(@Nullable Constant value, @NonNull AnnotationValue<ConstantRetryValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getMaxDuration() != null && value.getInterval() != null && value.getMaxDuration().compareTo(value.getInterval()) <= 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate( "'interval' must be less than 'maxDuration' but is " + value.getInterval())
                .addConstraintViolation();
            return false;
        }
        return true;
    }
}
