package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class DayOfWeekFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void mondayReturnsMonday() throws IllegalVariableEvaluationException {
        // 2025-01-06 is a Monday
        String result = variableRenderer.render(
            "{{ dayOfWeek('2025-01-06') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("MONDAY");
    }

    @Test
    void sundayReturnsSunday() throws IllegalVariableEvaluationException {
        // 2025-01-05 is a Sunday
        String result = variableRenderer.render(
            "{{ dayOfWeek('2025-01-05') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("SUNDAY");
    }

    @Test
    void saturdayReturnsSaturday() throws IllegalVariableEvaluationException {
        // 2025-01-04 is a Saturday
        String result = variableRenderer.render(
            "{{ dayOfWeek('2025-01-04') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("SATURDAY");
    }

    @Test
    void wednesdayReturnsWednesday() throws IllegalVariableEvaluationException {
        // 2025-01-08 is a Wednesday — midweek sanity check
        String result = variableRenderer.render(
            "{{ dayOfWeek('2025-01-08') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("WEDNESDAY");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate; 2025-01-06 is a Monday
        String result = variableRenderer.render(
            "{{ dayOfWeek('2025-01-06T10:00:00+02:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("MONDAY");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ dayOfWeek() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ dayOfWeek('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
