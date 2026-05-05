package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class DayOfMonthFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void returnsFirstDayOfMonth() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ dayOfMonth('2025-01-01') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("1");
    }

    @Test
    void returnsLastDayOfMonth() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ dayOfMonth('2025-01-31') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("31");
    }

    @Test
    void returnsMidMonthDay() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ dayOfMonth('2025-03-15') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("15");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate
        String result = variableRenderer.render(
            "{{ dayOfMonth('2025-01-15T10:00:00+02:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("15");
    }

    @Test
    void returnsValueFromTemplateVariable() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ dayOfMonth(dt) }}", Map.of("dt", "2025-06-20")
        );
        assertThat(result).isEqualTo("20");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ dayOfMonth() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingVariableThrows() {
        // Variable present in template but absent from context resolves to null
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ dayOfMonth(dt) }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ dayOfMonth('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
