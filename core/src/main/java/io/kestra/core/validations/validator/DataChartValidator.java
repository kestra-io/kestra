package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.EnumUtils;
import org.slf4j.event.Level;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.OrderBy;
import io.kestra.core.models.dashboards.WithColorByValues;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.filters.DurationFilters;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.validations.DataChartValidation;
import io.kestra.plugin.core.dashboard.data.Executions;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DataChartValidator implements ConstraintValidator<DataChartValidation, DataChart<?, ?>> {
    private static final Pattern HEX_COLOR = Pattern.compile("#(\\p{XDigit}{3,4}|\\p{XDigit}{6}|\\p{XDigit}{8})");

    @Inject
    private RepositoryConfiguration repositoryConfiguration;

    @Override
    public boolean isValid(
        @Nullable DataChart<?, ?> dataChart,
        @NonNull AnnotationValue<DataChartValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (dataChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        var columns = MapUtils.emptyOnNull(dataChart.getData().getColumns());
        Set<String> dataColumns = columns.keySet();
        if (columns.isEmpty()) {
            violations.add("`data.columns` is required and must declare at least one column.");
        }
        if (dataChart.getChartOptions() != null) {
            List<String> neededColumns = dataChart.getChartOptions().neededColumns();
            neededColumns.forEach(column ->
            {
                if (!dataColumns.contains(column)) {
                    violations.add("Column '" + column + "' is requested by the chart but not present in `data.columns` keys.");
                }
            });
        }

        if (dataChart.getChartOptions() instanceof WithColorByValues withColorByValues) {
            Set<String> listedValues = new HashSet<>();
            ListUtils.emptyOnNull(withColorByValues.getColorByValues()).stream().filter(Objects::nonNull).forEach(entry ->
            {
                if (entry.getValue() != null && !listedValues.add(entry.getValue())) {
                    violations.add("Value '%s' is listed more than once in `colorByValues`.".formatted(entry.getValue()));
                }
                if (entry.getColor() != null && !isColor(entry.getColor())) {
                    violations.add(
                        "Color '%s' of value '%s' is neither a hex color such as #d32f2f nor an execution state or log level name such as failed, warning or success."
                            .formatted(entry.getColor(), entry.getValue())
                    );
                }
            });
        }

        List<OrderBy> orderBy = dataChart.getData().getOrderBy();
        if (orderBy != null) {
            orderBy.stream().map(OrderBy::getColumn).forEach(column ->
            {
                if (!dataColumns.contains(column)) {
                    violations.add("Column '" + column + "' is used in `orderBy` but not present in `data.columns` keys.");
                }
            });
        }

        columns.forEach((key, value) ->
        {
            if (value.getField() == null && value.getAgg() != AggregationType.COUNT) {
                violations.add("Column '" + key + "' doesn't have a field to select from.");
            }
        });

        Integer minNumberOfAggregations = dataChart.minNumberOfAggregations();
        Integer maxNumberOfAggregations = dataChart.maxNumberOfAggregations();
        List<? extends ColumnDescriptor<?>> aggregationsColumns = columns.values().stream().filter(column -> column.getAgg() != null).toList();
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
            List<?> aggregationForbiddenFieldsUsed = columns.values().stream()
                .map(ColumnDescriptor::getField)
                .filter(Objects::nonNull)
                .filter(dataChart.getData().aggregationForbiddenFields()::contains).toList();
            if (!aggregationForbiddenFieldsUsed.isEmpty()) {
                violations.add(aggregationForbiddenFieldsUsed + " can't be used as or with aggregations.");
            }
        }

        Set<String> usedFields = columns.values().stream().map(c -> c.getAgg() + "-" + c.getField() + "-" + c.getKey()).collect(Collectors.toSet());
        if (usedFields.size() != columns.size()) {
            violations.add("Fields can only appear once in `data.columns`.");
        }

        if (
            columns.entrySet().stream().anyMatch(entry -> entry.getValue().getField() != null && entry.getValue().getField().equals(Executions.Fields.LABELS))
                && !("elasticsearch".equals(repositoryConfiguration.type()))
        ) {
            violations.add("LABELS column is only supported with an ElasticSearch database.");
        }

        violations.addAll(DurationFilters.violations(dataChart.getData().getWhere(), dataChart.getData().durationFields()));

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid data chart: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

    private static boolean isColor(String color) {
        return HEX_COLOR.matcher(color).matches()
            || EnumUtils.isValidEnumIgnoreCase(State.Type.class, color)
            || EnumUtils.isValidEnumIgnoreCase(Level.class, color);
    }
}
