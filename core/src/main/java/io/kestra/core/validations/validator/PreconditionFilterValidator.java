package io.kestra.core.validations.validator;

import io.kestra.core.validations.PreconditionFilterValidation;
import io.kestra.plugin.core.trigger.Flow;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class PreconditionFilterValidator implements ConstraintValidator<PreconditionFilterValidation, Flow.Filter> {
    @Override
    public boolean isValid(@Nullable Flow.Filter value, @NonNull AnnotationValue<PreconditionFilterValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        List<Flow.Type> needsValue = List.of(Flow.Type.EQUAL_TO, Flow.Type.NOT_EQUAL_TO, Flow.Type.IS_NULL, Flow.Type.IS_NOT_NULL, Flow.Type.IS_TRUE, Flow.Type.IS_FALSE, Flow.Type.STARTS_WITH, Flow.Type.ENDS_WITH, Flow.Type.REGEX, Flow.Type.CONTAINS);
        if (needsValue.contains(value.getType()) && value.getValue() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`value` cannot be null for type " + value.getType())
                .addConstraintViolation();
            return false;
        } else if (!needsValue.contains(value.getType()) && value.getValue() != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`value` must be null for type " + value.getType())
                .addConstraintViolation();
            return false;
        }

        List<Flow.Type> needsValues = List.of(Flow.Type.IN, Flow.Type.NOT_IN);
        if (needsValues.contains(value.getType()) && value.getValues() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`values` cannot be null for type " + value.getType())
                .addConstraintViolation();
            return false;
        } else if (!needsValues.contains(value.getType()) && value.getValues() != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`values` must be null for type " + value.getType())
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
