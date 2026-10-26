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

    @Test
    void shouldRejectLabelsColumnWhenRepositoryIsNotElasticsearch() {
        Chart<?> chart = YamlParser.parse(
            """
                id: labelled_executions
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: Labelled executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                  columns:
                    field: LABELS
                    key: env
                    agg: COUNT
                """,
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).isEqualTo(": Invalid data chart: The column of chart 'labelled_executions' uses the LABELS field, which requires an Elasticsearch repository (this instance uses 'h2'). Filter on a label in `data.where` or `data.numerator` or use another field instead.\n");
    }

    @Test
    void shouldRejectKPIChartWhenColumnsAreMissing() {
        Chart<?> chart = YamlParser.parse(
            """
                id: kpi_without_columns
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: KPI without columns
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                """,
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).isEqualTo(": Invalid data chart: Column is required.\n");
    }
}
