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
class IsDateAfterFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldReturnTrueWhenDateIsAfterReference() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-02', '2025-01-01') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldReturnFalseWhenDateIsBeforeReference() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01', '2025-01-02') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void shouldReturnFalseWhenDatesAreEqual() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01', '2025-01-01') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void shouldReturnTrueWhenZonedDateTimeIsAfterReference() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01T11:00:00Z', '2025-01-01T10:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldReturnFalseWhenZonedDateTimeIsBeforeReference() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01T10:00:00Z', '2025-01-01T11:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void shouldReturnFalseWhenZonedDateTimesAreEqual() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01T10:00:00Z', '2025-01-01T10:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void shouldReturnTrueWhenLocalDateTimeIsAfterReference() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01T10:00:00', '2025-01-01T09:00:00') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldAccountForTimezoneOffsetWhenComparing() throws IllegalVariableEvaluationException {
        // 11:00+02:00 == 09:00Z, which is after 08:00Z
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01T11:00:00+02:00', '2025-01-01T08:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldSupportMixedDateAndDatetime() throws IllegalVariableEvaluationException {
        // "2025-01-02" treated as 2025-01-02T00:00:00Z, which is after 2025-01-01T23:00:00Z
        String result = variableRenderer.render("{{ isDateAfter('2025-01-02', '2025-01-01T23:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldReturnFalseWhenMixedDateAndDatetimeMapToSameInstant() throws IllegalVariableEvaluationException {
        // "2025-01-01" treated as 2025-01-01T00:00:00Z — equal instants are not strictly after
        String result = variableRenderer.render("{{ isDateAfter('2025-01-01', '2025-01-01T00:00:00Z') }}", Collections.emptyMap());
        assertThat(result).isEqualTo("false");
    }

    @Test
    void shouldAcceptTemplateVariables() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ isDateAfter(d1, d2) }}", Map.of("d1", "2025-06-30", "d2", "2025-06-01"));
        assertThat(result).isEqualTo("true");
    }

    @Test
    void shouldThrowWhenDateArgumentIsMissing() {
        assertThatThrownBy(() -> variableRenderer.render("{{ isDateAfter() }}", Collections.emptyMap()))
            .isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void shouldThrowWhenReferenceArgumentIsMissing() {
        assertThatThrownBy(() -> variableRenderer.render("{{ isDateAfter('2025-01-01') }}", Collections.emptyMap()))
            .isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void shouldThrowWhenDateIsInvalid() {
        assertThatThrownBy(() -> variableRenderer.render("{{ isDateAfter('not-a-date', '2025-01-01') }}", Collections.emptyMap()))
            .isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void shouldThrowWhenReferenceIsInvalid() {
        assertThatThrownBy(() -> variableRenderer.render("{{ isDateAfter('2025-01-01', 'not-a-date') }}", Collections.emptyMap()))
            .isInstanceOf(IllegalVariableEvaluationException.class);
    }
}
