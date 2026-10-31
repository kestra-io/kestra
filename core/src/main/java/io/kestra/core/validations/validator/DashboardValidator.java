package io.kestra.core.validations.validator;

import java.util.List;
import java.util.Objects;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.validations.DashboardValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class DashboardValidator implements ConstraintValidator<DashboardValidation, Dashboard> {

    @Override
    public boolean isValid(
        @Nullable Dashboard value,
        @NonNull AnnotationValue<DashboardValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> duplicateIds = FlowValidator.getDuplicates(ListUtils.emptyOnNull(value.getCharts()).stream()
            .filter(Objects::nonNull)
            .map(Chart::getId)
            .filter(Objects::nonNull)
            .toList());
        if (duplicateIds.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Duplicate chart id [%s]: each chart of a dashboard needs its own id.".formatted(String.join(", ", duplicateIds)))
            .addConstraintViolation();
        return false;
    }
}
