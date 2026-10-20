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
class LabelsColumnValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldNameTheChartAndColumnWhenALabelsColumnIsUsedWithoutElasticsearch() {
        var chart = YamlParser.parse(
            """
                id: executions_per_country
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Executions per country
                  column: date
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                  columns:
                    date:
                      field: START_DATE
                    country:
                      field: LABELS
                      labelKey: country
                    total:
                      agg: COUNT
                """,
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains(
            "Column 'country' of chart 'executions_per_country' uses the LABELS field, which requires an Elasticsearch repository (this instance uses 'h2'). Filter on a label in `data.where` or use another field instead."
        );
    }

    @Test
    void shouldNameTheChartWhenAKPIColumnUsesLabelsWithoutElasticsearch() {
        var chart = YamlParser.parse(
            """
                id: labelled_executions
                type: io.kestra.plugin.core.dashboard.chart.KPI
                chartOptions:
                  displayName: Labelled executions
                data:
                  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                  columns:
                    field: LABELS
                    agg: COUNT
                """,
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains(
            "The column of chart 'labelled_executions' uses the LABELS field, which requires an Elasticsearch repository (this instance uses 'h2')."
        );
    }

    @Test
    void shouldAcceptALabelFilterWithoutElasticsearch() {
        var chart = YamlParser.parse(
            """
                id: executions_in_france
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Executions in France
                  column: date
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                  columns:
                    date:
                      field: START_DATE
                    state:
                      field: STATE
                    total:
                      agg: COUNT
                  where:
                    - field: LABELS
                      key: country
                      type: EQUAL_TO
                      value: FR
                """,
            Chart.class
        );

        assertThat(modelValidator.isValid(chart)).isEmpty();
    }
}
