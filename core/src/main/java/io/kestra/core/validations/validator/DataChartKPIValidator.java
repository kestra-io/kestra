package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.DurationFilters;
import io.kestra.core.validations.DataChartKPIValidation;
import io.kestra.plugin.core.dashboard.data.Executions;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DataChartKPIValidator implements ConstraintValidator<DataChartKPIValidation, DataChartKPI<?, ?>> {
    @Inject
    private RepositoryConfiguration repositoryConfiguration;

    @Override
    public boolean isValid(
        @Nullable DataChartKPI<?, ?> dataChart,
        @NonNull AnnotationValue<DataChartKPIValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (dataChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (dataChart.getData().getColumns() == null) {
            violations.add("Column is required.");
        } else {
            if (dataChart.getData().getColumns().getAgg() == null) {
                violations.add("Agg on column is required.");
            }

            if (Executions.Fields.LABELS.equals(dataChart.getData().getColumns().getField()) && !"elasticsearch".equals(repositoryConfiguration.type())) {
                violations.add(
                    "The column of chart '%s' uses the LABELS field, which requires an Elasticsearch repository (this instance uses '%s'). Filter on a label in `data.where` or `data.numerator` or use another field instead."
                        .formatted(dataChart.getId(), repositoryConfiguration.type())
                );
            }
        }

        violations.addAll(missingFieldViolations("where", dataChart.getData().getWhere()));
        violations.addAll(missingFieldViolations("numerator", dataChart.getData().getNumerator()));
        violations.addAll(DurationFilters.violations(dataChart.getData().getWhere(), dataChart.getData().durationFields()));
        violations.addAll(DurationFilters.violations(dataChart.getData().getNumerator(), dataChart.getData().durationFields()));

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid data chart: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

    private static List<String> missingFieldViolations(String property, @Nullable List<? extends AbstractFilter<?>> filters) {
        if (filters == null) {
            return List.of();
        }

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).getField() == null) {
                violations.add("`data.%s[%d].field` is required.".formatted(property, i));
            }
        }
        return violations;
    }
}
