package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatCode;

@KestraTest
class DataChartMissingColumnsValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldNotFailForTableWithoutColumns() {
        Chart<?> chart = YamlParser.parse(
            """
                id: executions_table
                type: io.kestra.plugin.core.dashboard.chart.Table
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                """,
            Chart.class
        );

        assertThatCode(() -> modelValidator.isValid(chart)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotFailForTimeSeriesWithoutColumns() {
        Chart<?> chart = YamlParser.parse(
            """
                id: executions_over_time
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                """,
            Chart.class
        );

        assertThatCode(() -> modelValidator.isValid(chart)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotFailForKPIWithoutColumns() {
        Chart<?> chart = YamlParser.parse(
            """
                id: executions_kpi
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: Executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                """,
            Chart.class
        );

        assertThatCode(() -> modelValidator.isValid(chart)).doesNotThrowAnyException();
    }
}
