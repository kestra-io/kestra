package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.dashboards.filters.Or;
import io.kestra.core.validations.OrFilterValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class OrFilterValidator implements ConstraintValidator<OrFilterValidation, Or<?>> {
    @Override
    public boolean isValid(
        @Nullable Or<?> orFilter,
        @NonNull AnnotationValue<OrFilterValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (orFilter == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (orFilter.getField() != null) {
            violations.add("Or filters can't have field specified at their root.");
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid Chart: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

}
