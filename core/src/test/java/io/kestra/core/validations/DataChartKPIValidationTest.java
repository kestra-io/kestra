package io.kestra.core.validations;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DataChartKPIValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldRejectExecutionsKPINumeratorLabelFilterWithoutKey() {
        Chart<?> chart = YamlParser.parse(
            """
                id: kpi_success_ratio
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: Success Ratio
                  numberType: PERCENTAGE
                  width: 3
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                  columns:
                    field: ID
                    agg: COUNT
                  numerator:
                    - type: EQUAL_TO
                      field: LABELS
                      value: prod
                """,
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("Label filters must have a `key`.");
    }
}
