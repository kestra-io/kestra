package io.kestra.core.validations.validator;

import io.kestra.core.validations.ConditionValidation;
import io.kestra.plugin.core.trigger.Flow;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class ConditionValidator implements ConstraintValidator<ConditionValidation, Flow> {
    @Override
    public boolean isValid(@Nullable Flow value, @NonNull AnnotationValue<ConditionValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return value.getConditions() != null || value.getPreconditions() != null;
    }
}
