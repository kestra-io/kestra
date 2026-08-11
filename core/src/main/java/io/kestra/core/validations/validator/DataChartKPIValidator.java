package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
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

        if (dataChart.getData().getColumns() != null) {
            if (dataChart.getData().getColumns().getAgg() == null) {
                violations.add("Agg on column is required.");
            }
        }

        if (
            dataChart.getData().getColumns().getField() != null && dataChart.getData().getColumns().getField().equals(Executions.Fields.LABELS)
                && !("elasticsearch".equals(repositoryConfiguration.type()))
        ) {
            violations.add("LABELS column is only supported with an ElasticSearch database.");
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
