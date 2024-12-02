package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.OrderBy;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Introspected
public class DataChartValidator implements ConstraintValidator<DataChartValidation, DataChart<?, ?>> {
    @Override
    public boolean isValid(
        @Nullable DataChart<?, ?> dataChart,
        @NonNull AnnotationValue<DataChartValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (dataChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        Set<String> dataColumns = dataChart.getData().getColumns().keySet();
        if (dataChart.getChartOptions() != null) {
            List<String> neededColumns = dataChart.getChartOptions().neededColumns();
            neededColumns.forEach(column -> {
                if (!dataColumns.contains(column)) {
                    violations.add("Column '" + column + "' is requested by the chart but not present in `data.columns` keys.");
                }
            });
        }

        List<OrderBy> orderBy = dataChart.getData().getOrderBy();
        if (orderBy != null) {
            orderBy.stream().map(OrderBy::getColumn).forEach(column -> {
                if (!dataColumns.contains(column)) {
                    violations.add("Column '" + column + "' is used in `orderBy` but not present in `data.columns` keys.");
                }
            });
        }

        dataChart.getData().getColumns().forEach((key, value) -> {
            if (value.getField() == null && value.getAgg() != AggregationType.COUNT) {
                violations.add("Column '" + key + "' doesn't have a field to select from.");
            }
        });

        Integer minNumberOfAggregations = dataChart.minNumberOfAggregations();
        Integer maxNumberOfAggregations = dataChart.maxNumberOfAggregations();
        List<? extends ColumnDescriptor<?>> aggregationsColumns = dataChart.getData().getColumns().values().stream().filter(column -> column.getAgg() != null).toList();
        if (minNumberOfAggregations != null) {
            if (aggregationsColumns.size() < minNumberOfAggregations) {
                violations.add("At least " + minNumberOfAggregations + " aggregation is needed for " + dataChart.getClass().getName() + ".");
            }
        }
        if (maxNumberOfAggregations != null) {
            if (aggregationsColumns.size() > maxNumberOfAggregations) {
                violations.add("At most " + minNumberOfAggregations + " aggregation can be provided for " + dataChart.getClass().getName() + ".");
            }
        }

        if (!aggregationsColumns.isEmpty()) {
            List<?> aggregationForbiddenFieldsUsed = dataChart.getData().getColumns().values().stream()
                .map(ColumnDescriptor::getField)
                .filter(Objects::nonNull)
                .filter(dataChart.getData().aggregationForbiddenFields()::contains).toList();
            if (!aggregationForbiddenFieldsUsed.isEmpty()) {
                violations.add(aggregationForbiddenFieldsUsed + " can't be used as or with aggregations.");
            }
        }

        Set<String> usedFields = dataChart.getData().getColumns().values().stream().map(c -> c.getAgg() + "-" + c.getField() + "-" + c.getLabelKey()).collect(Collectors.toSet());
        if (usedFields.size() != dataChart.getData().getColumns().size()) {
            violations.add("Fields can only appear once in `data.columns`.");
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
