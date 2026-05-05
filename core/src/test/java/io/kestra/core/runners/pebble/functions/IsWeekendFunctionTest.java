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
class IsWeekendFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void saturdayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-04 is a Saturday
        String result = variableRenderer.render(
            "{{ isWeekend('2025-01-04') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void sundayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-05 is a Sunday
        String result = variableRenderer.render(
            "{{ isWeekend('2025-01-05') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void weekdayReturnsFalse() throws IllegalVariableEvaluationException {
        // 2025-01-06 is a Monday
        String result = variableRenderer.render(
            "{{ isWeekend('2025-01-06') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate; 2025-01-04 is a Saturday
        String result = variableRenderer.render(
            "{{ isWeekend('2025-01-04T15:30:00+05:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isWeekend() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isWeekend('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
