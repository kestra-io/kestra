package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.GraphStyle;
import io.kestra.core.validations.TimeSeriesChartValidation;
import io.kestra.plugin.core.dashboard.chart.TimeSeries;
import io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor;
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
public class TimeSeriesChartValidator implements ConstraintValidator<TimeSeriesChartValidation, TimeSeries<?, ?>> {
    @Override
    public boolean isValid(
        @Nullable TimeSeries<?, ?> timeSeriesChart,
        @NonNull AnnotationValue<TimeSeriesChartValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (timeSeriesChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        timeSeriesChart.getData().getColumns().entrySet().forEach(columnWithKey -> {
            GraphStyle graphStyle = columnWithKey.getValue().getGraphStyle();
            if (columnWithKey.getValue().getAgg() == null && graphStyle != null) {
                violations.add("Only aggregations can have `graphStyle` specified and " + columnWithKey.getKey() + " is not one.");
            }
        });

        List<? extends TimeSeriesColumnDescriptor<?>> aggregationsColumns = timeSeriesChart.getData().getColumns().values().stream().filter(column -> column.getAgg() != null).toList();
        Set<GraphStyle> graphStyles = aggregationsColumns.stream().map(TimeSeriesColumnDescriptor::getGraphStyle).filter(Objects::nonNull).collect(Collectors.toSet());
        if (graphStyles.size() != aggregationsColumns.size()) {
            violations.add("All aggregations must have unique `graphStyle`.");
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
