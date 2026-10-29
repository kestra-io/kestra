package io.kestra.core.validations;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DataChartValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @ParameterizedTest
    @ValueSource(strings = {
        "io.kestra.plugin.core.dashboard.chart.Table:io.kestra.plugin.core.dashboard.data.Executions",
        "io.kestra.plugin.core.dashboard.chart.TimeSeries:io.kestra.plugin.core.dashboard.data.Logs",
    })
    void shouldReportMissingColumnsWhenDataHasNone(String chartAndDataTypes) {
        String[] types = chartAndDataTypes.split(":");
        Chart<?> chart = YamlParser.parse(
            """
                id: no_columns
                type: %s
                chartOptions:
                  displayName: No columns
                data:
                  type: %s
                """.formatted(types[0], types[1]),
            Chart.class
        );

        Optional<ConstraintViolationException> valid = modelValidator.isValid(chart);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("data.columns: must not be null");
    }
}
