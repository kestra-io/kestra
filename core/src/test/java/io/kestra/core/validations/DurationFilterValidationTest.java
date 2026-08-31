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
class DurationFilterValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptADurationFilterWrittenAsADuration() {
        assertThat(modelValidator.isValid(chartFilteringDurationBy("PT1S")).isEmpty()).isTrue();
    }

    @Test
    void shouldRejectADurationFilterThatIsNotADuration() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(chartFilteringDurationBy("1 second"));

        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("`1 second` is not a valid duration");
    }

    private static Chart<?> chartFilteringDurationBy(String value) {
        return YamlParser.parse(
            """
                id: executions_by_state
                type: io.kestra.plugin.core.dashboard.chart.Table
                chartOptions:
                  displayName: Executions by state
                  pagination:
                    enabled: false
                data:
                  type: io.kestra.plugin.core.dashboard.data.Executions
                  columns:
                    state:
                      field: STATE
                    count:
                      agg: COUNT
                  where:
                    - field: DURATION
                      type: GREATER_THAN
                      value: "%s"
                """.formatted(value),
            Chart.class
        );
    }
}
