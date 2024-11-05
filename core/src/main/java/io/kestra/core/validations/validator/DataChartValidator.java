package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.validations.DataChartValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
@Introspected
public class DataChartValidator implements ConstraintValidator<DataChartValidation, DataChart<?, ?>> {
    @Override
    public boolean isValid(
        @Nullable DataChart<?, ?> value,
        @NonNull AnnotationValue<DataChartValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getChartOptions() == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        Set<String> dataColumns = value.getData().getColumns().keySet();
        List<String> neededColumns = value.getChartOptions().neededColumns();
        neededColumns.forEach(column -> {
            if (!dataColumns.contains(column)) {
                violations.add("Column '" + column + "' is requested by the chart but not present in `data.columns` keys.");
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
