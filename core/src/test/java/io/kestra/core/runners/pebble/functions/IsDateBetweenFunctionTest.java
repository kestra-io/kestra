package io.kestra.core.runners.pebble.functions;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class IsDateBetweenFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @ParameterizedTest
    @CsvSource({
        "2026-03-01T00:00:00Z, true",
        "2025-12-31T23:59:59Z, false",
        "2026-06-30T23:59:59Z, false",
        "2025-06-01T00:00:00Z, false",
        "2026-09-01T00:00:00Z, false",
    })
    void comparesStringDateAgainstExclusiveBounds(String date, boolean expected) throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ isDateBetween(date, '2025-12-31T23:59:59Z', '2026-06-30T23:59:59Z') }}", Map.of("date", date)
        );
        assertThat(result).isEqualTo(String.valueOf(expected));
    }

    @Test
    void acceptsZonedDateTimeAsInsideScheduleWhenCondition() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ isDateBetween(trigger.date, '2025-12-31T23:59:59Z', '2026-06-30T23:59:59Z') }}",
            Map.of("trigger", Map.of("date", ZonedDateTime.parse("2026-03-01T10:00:00+01:00")))
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void comparesInstantsRatherThanText() throws IllegalVariableEvaluationException {
        // 00:00-03:00 is 03:00Z, so it falls after 02:00Z even though it sorts before it as text
        String result = variableRenderer.render(
            "{{ isDateBetween('2026-01-01T00:00:00-03:00', '2026-01-01T02:00:00Z', '2026-01-01T04:00:00Z') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void worksAsBooleanCondition() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{% if isDateBetween('2026-03-01T00:00:00Z', '2025-12-31T23:59:59Z', '2026-06-30T23:59:59Z') %}yes{% else %}no{% endif %}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("yes");
    }

    @Test
    void datetimeWithoutOffsetThrows() {
        assertThatThrownBy(
            () -> variableRenderer.render(
                "{{ isDateBetween('2026-03-01T00:00:00', '2025-12-31T23:59:59Z', '2026-06-30T23:59:59Z') }}", Collections.emptyMap()
            )
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("could not parse 'date'");
    }

    @Test
    void missingBoundThrows() {
        assertThatThrownBy(
            () -> variableRenderer.render(
                "{{ isDateBetween('2026-03-01T00:00:00Z', '2025-12-31T23:59:59Z') }}", Collections.emptyMap()
            )
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("expects a 'before' argument");
    }

    @Test
    void invalidBoundThrows() {
        assertThatThrownBy(
            () -> variableRenderer.render(
                "{{ isDateBetween('2026-03-01T00:00:00Z', 'not-a-date', '2026-06-30T23:59:59Z') }}", Collections.emptyMap()
            )
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("could not parse 'after'");
    }
}
