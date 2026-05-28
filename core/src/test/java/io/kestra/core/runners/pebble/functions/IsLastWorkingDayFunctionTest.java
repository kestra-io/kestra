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
class IsLastWorkingDayFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void lastFridayWhenMonthEndsOnSundayReturnsTrue() throws IllegalVariableEvaluationException {
        // November 2025 ends on Sunday (Nov 30); last working day is Friday Nov 28
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-11-28') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void saturdayBeforeMonthEndReturnsFalse() throws IllegalVariableEvaluationException {
        // Nov 29 2025 is Saturday — not a working day
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-11-29') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void sundayAtMonthEndReturnsFalse() throws IllegalVariableEvaluationException {
        // Nov 30 2025 is Sunday — not a working day
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-11-30') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void lastWorkingDayWhenMonthEndsOnFridayReturnsTrue() throws IllegalVariableEvaluationException {
        // January 2025 ends on Friday (Jan 31)
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-31') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void penultimateWorkingDayReturnsFalse() throws IllegalVariableEvaluationException {
        // Jan 30 2025 is Thursday — one day before the last working day (Jan 31 Friday)
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-30') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void lastWorkingDayWhenMonthEndsOnWednesdayReturnsTrue() throws IllegalVariableEvaluationException {
        // September 2025 ends on Tuesday (Sep 30)
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-09-30') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Jan 31 2025 is a Friday (last working day); verify ZonedDateTime input is parsed correctly
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-31T09:00:00+05:30') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void customWorkingDaysReturnsTrue() throws IllegalVariableEvaluationException {
        // With Mon-Thu as working days, last working day of Jan 2025 is Thursday Jan 30
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-30', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void customWorkingDaysFridayNotLastReturnsFalse() throws IllegalVariableEvaluationException {
        // With Mon-Thu as working days, Jan 31 (Friday) is not a working day at all
        String result = variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-31', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void missingDateThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isLastWorkingDay() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isLastWorkingDay('not-a-date') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidWorkingDayNameThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isLastWorkingDay('2025-01-31', 'MONDAY,BLURSDAY') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
