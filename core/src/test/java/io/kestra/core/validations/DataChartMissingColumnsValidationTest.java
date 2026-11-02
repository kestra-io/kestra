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
class DataChartMissingColumnsValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldRejectTableWithoutColumns() {
        Optional<ConstraintViolationException> violation = validate(
            """
                id: executions_table
                type: io.kestra.plugin.core.dashboard.chart.Table
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                """
        );

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("`data.columns` is required");
    }

    @Test
    void shouldRejectTimeSeriesWithoutColumns() {
        Optional<ConstraintViolationException> violation = validate(
            """
                id: executions_over_time
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                """
        );

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("`data.columns` is required");
    }

    @Test
    void shouldRejectKPIWithoutColumns() {
        Optional<ConstraintViolationException> violation = validate(
            """
                id: executions_kpi
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                """
        );

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("`data.columns` is required");
    }

    private Optional<ConstraintViolationException> validate(String yaml) {
        return modelValidator.isValid(YamlParser.parse(yaml, Chart.class));
    }
}
