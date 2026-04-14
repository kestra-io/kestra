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
class IsDayWeekInMonthFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void firstMondayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-06 is the first Monday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06', 'MONDAY', 'FIRST') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void firstMondayOnWrongDateReturnsFalse() throws IllegalVariableEvaluationException {
        // 2025-01-13 is the second Monday, not the first
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-13', 'MONDAY', 'FIRST') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void secondMondayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-13 is the second Monday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-13', 'MONDAY', 'SECOND') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void thirdMondayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-20 is the third Monday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-20', 'MONDAY', 'THIRD') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void fourthMondayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-27 is the fourth Monday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-27', 'MONDAY', 'FOURTH') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void lastMondayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-27 is also the last Monday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-27', 'MONDAY', 'LAST') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void lastFridayReturnsTrue() throws IllegalVariableEvaluationException {
        // 2025-01-31 is the last (5th) Friday of January 2025
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-31', 'FRIDAY', 'LAST') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void lastVsFourthAreDistinct() throws IllegalVariableEvaluationException {
        // January 2025 has 5 Fridays: the 4th is Jan 24, the last (5th) is Jan 31
        String fourthOnLastDate = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-31', 'FRIDAY', 'FOURTH') }}", Collections.emptyMap()
        );
        assertThat(fourthOnLastDate).isEqualTo("false");

        String fourthOnCorrectDate = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-24', 'FRIDAY', 'FOURTH') }}", Collections.emptyMap()
        );
        assertThat(fourthOnCorrectDate).isEqualTo("true");
    }

    @Test
    void caseInsensitiveArgumentsAreAccepted() throws IllegalVariableEvaluationException {
        // dayOfWeek and position are normalised to upper-case internally
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06', 'monday', 'first') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate; 2025-01-06 is a Monday
        String result = variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06T10:00:00Z', 'MONDAY', 'FIRST') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingDayOfWeekThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingPositionThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06', 'MONDAY') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDayOfWeekThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06', 'BLURSDAY', 'FIRST') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidPositionThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth('2025-01-06', 'MONDAY', 'FIFTH') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isDayWeekInMonth('not-a-date', 'MONDAY', 'FIRST') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
