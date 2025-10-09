package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.validations.DataChartKPIValidation;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.micronaut.context.annotation.Value;
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
public class DataChartKPIValidator implements ConstraintValidator<DataChartKPIValidation, DataChartKPI<?, ?>> {
    @Value("${kestra.repository.type}")
    private String repositoryType;

    @Override
    public boolean isValid(
        @Nullable DataChartKPI<?, ?> dataChart,
        @NonNull AnnotationValue<DataChartKPIValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (dataChart == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if(dataChart.getData().getColumns() != null) {
            if (dataChart.getData().getColumns().getAgg() == null) {
                violations.add("Agg on column is required.");
            }
        }

        if (dataChart.getData().getColumns().getField() != null && dataChart.getData().getColumns().getField().equals(Executions.Fields.LABELS)
        && !repositoryType.equals("elasticsearch")) {
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
