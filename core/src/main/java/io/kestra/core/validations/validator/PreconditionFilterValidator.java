package io.kestra.core.validations.validator;

import io.kestra.core.validations.PreconditionFilterValidation;
import io.kestra.plugin.core.condition.AdvancedExecutionsCondition;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class PreconditionFilterValidator implements ConstraintValidator<PreconditionFilterValidation, AdvancedExecutionsCondition.Filter> {
    @Override
    public boolean isValid(@Nullable AdvancedExecutionsCondition.Filter value, @NonNull AnnotationValue<PreconditionFilterValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        List<AdvancedExecutionsCondition.Type> needsValue = List.of(AdvancedExecutionsCondition.Type.EQUAL_TO, AdvancedExecutionsCondition.Type.NOT_EQUAL_TO, AdvancedExecutionsCondition.Type.IS_NULL, AdvancedExecutionsCondition.Type.IS_NOT_NULL, AdvancedExecutionsCondition.Type.IS_TRUE, AdvancedExecutionsCondition.Type.IS_FALSE, AdvancedExecutionsCondition.Type.STARTS_WITH, AdvancedExecutionsCondition.Type.ENDS_WITH, AdvancedExecutionsCondition.Type.REGEX, AdvancedExecutionsCondition.Type.CONTAINS);
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

        List<AdvancedExecutionsCondition.Type> needsValues = List.of(AdvancedExecutionsCondition.Type.IN, AdvancedExecutionsCondition.Type.NOT_IN);
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
