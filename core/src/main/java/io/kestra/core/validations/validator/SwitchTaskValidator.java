package io.kestra.core.validations.validator;

import io.kestra.core.tasks.flows.Switch;
import io.kestra.core.validations.SwitchTaskValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class SwitchTaskValidator implements ConstraintValidator<SwitchTaskValidation, Switch> {
    @Override
    public boolean isValid(
        @Nullable Switch value,
        @NonNull AnnotationValue<SwitchTaskValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if ((value.getCases() == null || value.getCases().isEmpty()) &&
            (value.getDefaults() == null || value.getDefaults().isEmpty())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("No task defined, neither cases or default have any tasks")
                .addConstraintViolation();

            return false;
        }

        return true;
    }
}
