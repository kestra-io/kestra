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
class DataChartColorsValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptHexColorsAndStateOrLevelNames() {
        Optional<ConstraintViolationException> violation = validate("""
            - value: by_priority.critical
              label: Critical
              color: "#d32f2f"
            - value: high
              color: warning
            - value: medium
              color: "#F9A825"
            - value: low
              color: SUCCESS
            - value: 1
              color: info
            - value: unlabelled
            """);

        assertThat(violation).isEmpty();
    }

    @Test
    void shouldRejectColorThatIsNeitherHexNorStateOrLevelName() {
        Optional<ConstraintViolationException> violation = validate("""
            - value: critical
              color: red
            """);

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("Color 'red' of value 'critical'");
    }

    @Test
    void shouldRejectStateThatHasNoColor() {
        Optional<ConstraintViolationException> violation = validate("""
            - value: queued
              color: submitted
            """);

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("Color 'submitted' of value 'queued'");
    }

    @Test
    void shouldRejectValueListedTwice() {
        Optional<ConstraintViolationException> violation = validate("""
            - value: critical
              color: failed
            - value: critical
              label: Critical
            """);

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("Value 'critical' is listed more than once");
    }

    private Optional<ConstraintViolationException> validate(String colorByValues) {
        String yaml = """
            id: open_by_priority
            type: io.kestra.plugin.core.dashboard.chart.Pie
            chartOptions:
              displayName: Open issues by priority
              colorByColumn: state
              colorByValues:
            %s
            data:
              type: io.kestra.plugin.core.dashboard.data.Executions
              columns:
                state:
                  field: STATE
                total:
                  agg: COUNT
            """.formatted(colorByValues.indent(4));
        return modelValidator.isValid(YamlParser.parse(yaml, Chart.class));
    }
}
