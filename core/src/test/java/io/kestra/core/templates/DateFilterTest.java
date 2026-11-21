package io.kestra.core.templates;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.pebble.filters.DateFilter;

import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@TestInstance(Lifecycle.PER_CLASS)
class DateFilterTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void nanosecondEpochTimestamp() throws IllegalVariableEvaluationException {
        // 1_000_000_000_000_000_000L nanoseconds = 1_000_000_000 seconds = 2001-09-09T01:46:40Z
        String render1 = variableRenderer.render(
            "{{ 1000000000000000000 | date(format='yyyy-MM-dd HH:mm:ss', timeZone='UTC') }}",
            Map.of()
        );
        assertThat(render1).isEqualTo("2001-09-09 01:46:40");

        // Nanosecond timestamp with sub-second nanos that does not end with 000
        // (Previously corrupted by #20210 which overwrote milliseconds)
        long nanosWithSubseconds = 1_000_000_000_123_456_789L;
        String render2 = variableRenderer.render(
            "{{ nanoTimestamp | date(format='yyyy-MM-dd HH:mm:ss.SSSSSS', timeZone='UTC') }}",
            Map.of("nanoTimestamp", nanosWithSubseconds)
        );
        assertThat(render2).isEqualTo("2001-09-09 01:46:40.123456");
    }

    @Test
    void dateFormattedAcrossDstBoundary() throws IllegalVariableEvaluationException {
        // On 2024-03-10, America/New_York transitioned from EST (UTC-5) to EDT (UTC-4) at 02:00 AM local time (07:00 UTC).
        // 06:59:59 UTC -> 01:59:59 EST (-05:00)
        // 07:00:00 UTC -> 03:00:00 EDT (-04:00) (the 2:00 AM hour is skipped during spring-forward)
        String render = variableRenderer.render(
            """
                {{ before | date(format='yyyy-MM-dd HH:mm:ss XXX', timeZone='America/New_York') }}
                {{ atTransition | date(format='yyyy-MM-dd HH:mm:ss XXX', timeZone='America/New_York') }}
                {{ springForwardGap | date(format='yyyy-MM-dd HH:mm:ss XXX', timeZone='America/New_York') }}
                """,
            Map.of(
                "before", "2024-03-10T06:59:59Z",
                "atTransition", "2024-03-10T07:00:00Z",
                "springForwardGap", "2024-03-10T02:00:00"
            )
        );

        assertThat(render).isEqualTo("""
            2024-03-10 01:59:59 -05:00
            2024-03-10 03:00:00 -04:00
            2024-03-10 03:00:00 -04:00
            """);
    }

    @Test
    void dateInNonUtcTimeZoneReturnsCorrectLocalDate() throws IllegalVariableEvaluationException {
        // 2024-01-01T23:30:00Z in UTC is 2024-01-01, but in Asia/Tokyo (+09:00) it is 2024-01-02 08:30:00
        String renderTokyo = variableRenderer.render(
            "{{ '2024-01-01T23:30:00Z' | date(format='yyyy-MM-dd', timeZone='Asia/Tokyo') }}",
            Map.of()
        );
        assertThat(renderTokyo).isEqualTo("2024-01-02");

        // 2024-01-02T02:30:00Z in UTC is 2024-01-02, but in America/New_York (-05:00) it is 2024-01-01 21:30:00
        String renderNewYork = variableRenderer.render(
            "{{ '2024-01-02T02:30:00Z' | date(format='yyyy-MM-dd', timeZone='America/New_York') }}",
            Map.of()
        );
        assertThat(renderNewYork).isEqualTo("2024-01-01");
    }

    @Test
    void nullInputReturnsEmptyString() throws Exception {
        // Direct filter application
        DateFilter filter = new DateFilter();
        Object directResult = filter.apply(null, Map.of("format", "yyyy-MM-dd"), null, null, 1);
        assertThat(directResult).isEqualTo("");

        // Template rendering with null variable
        Map<String, Object> vars = new HashMap<>();
        vars.put("nullDate", null);
        String render = variableRenderer.render("{{ nullDate | date('yyyy-MM-dd') }}", vars);
        assertThat(render).isEqualTo("");
    }

    @Test
    void invalidFormatStringRaisesHelpfulError() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ '2024-01-01' | date(format='invalid_pattern_qqq') }}", Map.of())
        );

        DateFilter filter = new DateFilter();
        PebbleException thrown = assertThrows(
            PebbleException.class,
            () -> filter.apply("2024-01-01", Map.of("format", "invalid_pattern_qqq"), null, null, 1)
        );
        assertThat(thrown.getMessage()).contains("Invalid date format");
    }
}
