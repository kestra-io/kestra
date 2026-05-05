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
class HourOfDayFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void returnsHourFromZonedDateTime() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ hourOfDay('2025-01-06T14:30:00Z') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("14");
    }

    @Test
    void returnsHourFromZonedDateTimeWithOffset() throws IllegalVariableEvaluationException {
        // Local hour is 14 regardless of the +02:00 offset — no UTC normalization
        String result = variableRenderer.render(
            "{{ hourOfDay('2025-01-06T14:30:00+02:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("14");
    }

    @Test
    void returnsHourFromLocalDateTime() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ hourOfDay('2025-01-06T09:00:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("9");
    }

    @Test
    void returnsMidnight() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ hourOfDay('2025-01-06T00:00:00Z') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("0");
    }

    @Test
    void returnsLastHourOfDay() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ hourOfDay('2025-01-06T23:59:59Z') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("23");
    }

    @Test
    void returnsHourFromTemplateVariable() throws IllegalVariableEvaluationException {
        // Verifies the args.get("date") path with a rendered variable, not just a literal
        String result = variableRenderer.render(
            "{{ hourOfDay(dt) }}", Map.of("dt", "2025-01-06T14:30:00Z")
        );
        assertThat(result).isEqualTo("14");
    }

    @Test
    void plainDateThrows() {
        // Plain date strings have no time component
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ hourOfDay('2025-01-06') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ hourOfDay() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ hourOfDay('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
