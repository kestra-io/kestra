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
class MonthOfYearFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void returnsJanuary() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ monthOfYear('2025-01-15') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("1");
    }

    @Test
    void returnsDecember() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ monthOfYear('2025-12-31') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("12");
    }

    @Test
    void returnsMidYearMonth() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ monthOfYear('2025-06-01') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("6");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate
        String result = variableRenderer.render(
            "{{ monthOfYear('2025-03-10T08:00:00+01:00') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("3");
    }

    @Test
    void returnsValueFromTemplateVariable() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render(
            "{{ monthOfYear(dt) }}", Map.of("dt", "2025-09-20")
        );
        assertThat(result).isEqualTo("9");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ monthOfYear() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingVariableThrows() {
        // Variable present in template but absent from context resolves to null
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ monthOfYear(dt) }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ monthOfYear('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
