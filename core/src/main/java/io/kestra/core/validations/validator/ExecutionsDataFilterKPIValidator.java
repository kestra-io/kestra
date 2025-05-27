package io.kestra.core.validations.validator;

import io.kestra.core.validations.ExecutionsDataFilterValidation;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.dashboard.data.ExecutionsKPI;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@Introspected
public class ExecutionsDataFilterKPIValidator implements ConstraintValidator<ExecutionsDataFilterValidation, ExecutionsKPI<?>> {
    @Override
    public boolean isValid(
        @Nullable ExecutionsKPI<?> executionsDataFilter,
        @NonNull AnnotationValue<ExecutionsDataFilterValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (executionsDataFilter == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (executionsDataFilter.getColumns().getField() == Executions.Fields.LABELS && executionsDataFilter.getColumns().getLabelKey() == null) {
            violations.add("Column must have a `labelKey`.");
        }

        executionsDataFilter.getNumerator().forEach(filter -> {
            if (filter.getField() == Executions.Fields.LABELS && filter.getLabelKey() == null) {
                violations.add("Label filters must have a `labelKey`.");
            }
        });

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
