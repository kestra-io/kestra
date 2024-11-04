package io.kestra.core.validations.validator;

import io.kestra.core.validations.ExecutionFiltersConditionValidation;
import io.kestra.plugin.core.condition.ExecutionFilters;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ExecutionFiltersConditionValidator  implements ConstraintValidator<ExecutionFiltersConditionValidation, ExecutionFilters.Condition> {
    @Override
    public boolean isValid(@Nullable ExecutionFilters.Condition value, @NonNull AnnotationValue<ExecutionFiltersConditionValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        List<ExecutionFilters.Type> needsValue = List.of(ExecutionFilters.Type.EQUAL_TO, ExecutionFilters.Type.NOT_EQUAL_TO, ExecutionFilters.Type.IS_NULL, ExecutionFilters.Type.IS_NOT_NULL, ExecutionFilters.Type.IS_TRUE, ExecutionFilters.Type.IS_FALSE, ExecutionFilters.Type.STARTS_WITH, ExecutionFilters.Type.ENDS_WITH, ExecutionFilters.Type.REGEX, ExecutionFilters.Type.CONTAINS);
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

        List<ExecutionFilters.Type> needsValues = List.of(ExecutionFilters.Type.IN, ExecutionFilters.Type.NOT_IN);
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
