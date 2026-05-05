package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class IsPublicHolidayFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void publicHolidayReturnsTrue() throws IllegalVariableEvaluationException {
        // Christmas Day is a public holiday in France
        String result = variableRenderer.render(
            "{{ isPublicHoliday('2024-12-25', 'FR') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void nonHolidayReturnsFalse() throws IllegalVariableEvaluationException {
        // A regular working day in France
        String result = variableRenderer.render(
            "{{ isPublicHoliday('2024-03-12', 'FR') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("false");
    }

    @Test
    void publicHolidayWithSubDivision() throws IllegalVariableEvaluationException {
        // Armistice Day (November 11) is a public holiday in France including Ile-de-France
        String result = variableRenderer.render(
            "{{ isPublicHoliday('2024-11-11', 'FR', 'IDF') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void datetimeStringIsAccepted() throws IllegalVariableEvaluationException {
        // Verifies the ZonedDateTime fallback in DateUtils.parseLocalDate; 2024-12-25 is a public holiday in France
        String result = variableRenderer.render(
            "{{ isPublicHoliday('2024-12-25T10:00:00Z', 'FR') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void blankSubDivisionFallsBackToCountry() throws IllegalVariableEvaluationException {
        // A blank subDivision is normalised to null and country-level check is applied
        String result = variableRenderer.render(
            "{{ isPublicHoliday('2024-11-11', 'FR', '') }}", Collections.emptyMap()
        );
        assertThat(result).isEqualTo("true");
    }

    @Test
    void missingDateThrows() {
        // No arguments — date is null
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isPublicHoliday() }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void missingCountryCodeThrows() {
        // Only date provided — countryCode is null
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isPublicHoliday('2024-12-25') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidDateFormatThrows() {
        assertThatThrownBy(() -> variableRenderer.render(
            "{{ isPublicHoliday('not-a-date', 'FR') }}", Collections.emptyMap()
        )).isInstanceOf(IllegalVariableEvaluationException.class);
    }

    @Test
    void invalidCountryCodeThrows() {
        var exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render(
            "{{ isPublicHoliday('2024-12-25', 'XX') }}", Collections.emptyMap()
        ));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }
}
