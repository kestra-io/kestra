package io.kestra.core.runners.pebble.functions;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class IsLastDayOfMonthFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @ParameterizedTest
    @CsvSource(
        {
            "2025-01-31, true",
            "2025-01-30, false",
            "2025-02-28, true",
            "2024-02-28, false",
            "2024-02-29, true",
            "1900-02-28, true",
            "2000-02-28, false",
            "2000-02-29, true",
            "2025-04-30, true",
            "2025-04-29, false",
            "2025-08-31, true",
            "2025-03-01, false",
            "2024-02-29T00:30:00+14:00, true",
            "2024-03-01T00:30:00+14:00, false",
            "2025-02-28T23:30:00-12:00, true",
            "2025-04-30T11:00:00, true"
        }
    )
    void shouldCheckTheLocalCalendarMonthBoundary(String date, boolean expected) throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.renderTyped("{{ isLastDayOfMonth(date) }}", Map.of("date", date))).isEqualTo(expected);
    }

    @Test
    void shouldAcceptTheScheduleDateObject() throws IllegalVariableEvaluationException {
        var date = ZonedDateTime.parse("2024-02-29T11:00:00+05:30[Asia/Kolkata]");
        assertThat(variableRenderer.renderTyped("{{ isLastDayOfMonth(trigger.date) }}", Map.of("trigger", Map.of("date", date)))).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{{ isLastDayOfMonth() }}", "{{ isLastDayOfMonth(null) }}", "{{ isLastDayOfMonth('not-a-date') }}", "{{ isLastDayOfMonth('2025-02-29') }}" })
    void shouldRejectMissingOrInvalidDates(String expression) {
        assertThatThrownBy(() -> variableRenderer.render(expression, Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("isLastDayOfMonth()");
    }
}
