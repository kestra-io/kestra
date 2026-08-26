package io.kestra.core.validations.validator;

import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.validations.FlowTriggerValidation;
import io.kestra.plugin.core.trigger.Flow;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class FlowTriggerValidator implements ConstraintValidator<FlowTriggerValidation, Flow> {
    @Override
    public boolean isValid(@Nullable Flow value, @NonNull AnnotationValue<FlowTriggerValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (MultipleCondition.Mode.AT_LEAST != value.getMode()) {
            return true;
        }

        if (value.getMinSatisfied() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`minSatisfied` must be set when mode is AT_LEAST")
                .addConstraintViolation();
            return false;
        }

        if (value.getDependsOn() != null && value.getMinSatisfied() > value.getDependsOn().size()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "`minSatisfied` cannot be greater than the number of `dependsOn` conditions (" + value.getDependsOn().size() + ")"
                )
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
