package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.validations.TableChartValidation;
import io.kestra.plugin.core.dashboard.chart.Table;
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
public class TableChartValidator implements ConstraintValidator<TableChartValidation, Table<?, ?>> {
    @Override
    public boolean isValid(
        @Nullable Table<?, ?> tableChart,
        @NonNull AnnotationValue<TableChartValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (tableChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        List<? extends ColumnDescriptor<?>> aggregationsColumns = tableChart.getData().getColumns().values().stream().filter(column -> column.getAgg() != null).toList();
        if (!aggregationsColumns.isEmpty() && tableChart.getChartOptions().getPagination().isEnabled()) {
            violations.add("Pagination can't be enabled when there is one or more aggregation(s). Please add `chartOptions.pagination.enabled: false` or remove your aggregation(s).");
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid data chart: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

}
