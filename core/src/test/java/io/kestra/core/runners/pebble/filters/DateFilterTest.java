package io.kestra.core.runners.pebble.filters;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@TestInstance(Lifecycle.PER_CLASS)
class DateFilterTest {
    public static final ZonedDateTime NOW = ZonedDateTime.parse("2013-09-08T16:19:12.123456+01");

    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void dateFormat() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "date", new Date(NOW.toEpochSecond() * 1000),
            "instant", NOW.toInstant(),
            "zoned", NOW,
            "local", NOW.toLocalDateTime()
        );

        String render = variableRenderer.render(
            """
                {{ date | date(format='iso', timeZone='Europe/Paris') }}
                {{ instant | date(format="iso_sec", timeZone="Europe/Paris") }}
                {{ instant | date(format="iso_milli", timeZone="Europe/Paris") }}
                {{ zoned | date(format="iso", timeZone="Europe/Paris") }}
                {{ zoned | date(format="iso_milli", timeZone="Europe/Paris") }}
                {{ local | date(format="yyyy-MM-dd HH:mm:ss.SSSSSSXXX", timeZone="Europe/Paris") }}
                {{ local | date(format="yyyy-MM-dd HH:mm:ss.SSS", timeZone="UTC") }}
                {{ local | date(format="sql", timeZone="UTC") }}
                {{ local | date(format="sql_sec", timeZone="UTC") }}
                {{ local | date(format="sql_milli", timeZone="UTC") }}
                {{ local | date(format="yyyy-MM-dd HH:mm:ss.SSSSSSXXX", timeZone="UTC") }}""",
            vars
        );

        assertThat(render).isEqualTo("""
            2013-09-08T17:19:12.000000+02:00
            2013-09-08T17:19:12+02:00
            2013-09-08T17:19:12.123+02:00
            2013-09-08T17:19:12.123456+02:00
            2013-09-08T17:19:12.123+02:00
            2013-09-08 16:19:12.123456+02:00
            2013-09-08 16:19:12.123
            2013-09-08 16:19:12.123456
            2013-09-08 16:19:12
            2013-09-08 16:19:12.123
            2013-09-08 16:19:12.123456Z""");
    }

    @Test
    void dateStringFormat() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            """
                {{ "July 24, 2001" | date("yyyy-MM-dd", existingFormat="MMMM dd, yyyy") }}
                {{ "2013-09-08T17:19:12+02:00" | date(timeZone="Europe/Paris") }}
                {{ "2013-09-08T17:19:12" | date(timeZone="Europe/Paris") }}
                {{ "2013-09-08" | date(timeZone="Europe/Paris") }}
                {{ "08.09.2023" | date("yyyy-MM-dd", existingFormat="dd.MM.yyyy") }}
                {{ "08092023" | date("yyyy-MM-dd", existingFormat="ddMMyyyy") }}
                """,
            Map.of()
        );

        assertThat(render).isEqualTo("""
            2001-07-24
            2013-09-08T17:19:12.000000+02:00
            2013-09-08T17:19:12.000000+02:00
            2013-09-08T00:00:00.000000+02:00
            2023-09-08
            2023-09-08
            """);
    }

    @Test
    void timestamp() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestamp(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("1378653552");
    }

    @Test
    void timestampCompare() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ (zoned | timestamp) > (zoned | dateAdd(-1, 'DAYS') | timestamp) }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("true");
    }

    @Test
    void dateRfc() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ 'Tue, 08 Feb 2022 19:38:26 GMT' | date(existingFormat='rfc_1123_date_time', timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("2022-02-08T20:38:26.000000+01:00");
    }

    @Test
    void instantNano() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestampNano(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("1378653552123456000");
    }

    @Test
    void instantMicro() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestampMicro(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("1378653552123456");
    }

    @Test
    void instantMilli() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestampMilli(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("1378653552123");
    }

    @Test
    void now() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ now() }}", ImmutableMap.of());
        assertThat(render).contains(ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        render = variableRenderer.render("{{ now(timeZone=\"Europe/Lisbon\") }}", ImmutableMap.of());

        assertThat(render).contains(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertThat(render).contains(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ofPattern("HH:mm")));

        render = variableRenderer.render("{{ now(format=\"iso_local_date\", timeZone=\"Europe/Lisbon\") }}", ImmutableMap.of());

        assertThat(render).isEqualTo(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ISO_LOCAL_DATE));

        render = variableRenderer.render("{{ now(format=\"sql_milli\") }}", ImmutableMap.of());

        // a millisecond can pass between the render and now so we can't assert on a precise to millisecond date
        assertThat(render).startsWith(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        assertThat(render).hasSize(23);
    }

    @Test
    void add() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | dateAdd(-1,\"DAYS\",timeZone=\"Europe/Paris\")}}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render).isEqualTo("2013-09-07T17:19:12.123456+02:00");
    }

    @Test
    void timestampDateFormat() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            """
                {{ 1378653552 | date(format="iso_sec", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="iso_milli", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="iso_zoned_date_time", timeZone="Europe/Paris") }}
                {{ 1378653552123456000 | date(format="iso", timeZone="Europe/Paris") }}
                {{ 1378653552123456789 | date(format="iso", timeZone="Europe/Paris") }}
                {{ 1378653552 | date(format="sql_sec", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="sql_milli", timeZone="Europe/Paris") }}
                {{ 1378653552123456000 | date(format="sql", timeZone="Europe/Paris") }}
                {{ 1378653552123456789 | date(format="sql", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="sql_milli", timeZone="UTC") }}
                {{ "1378653552123" | number | date(format="sql_milli", timeZone="UTC") }}
                """,
            Map.of()
        );

        assertThat(render).isEqualTo("""
            2013-09-08T17:19:12+02:00
            2013-09-08T17:19:12.123+02:00
            2013-09-08T17:19:12.123000+02:00
            2013-09-08T17:19:12.123+02:00[Europe/Paris]
            2013-09-08T17:19:12.123456+02:00
            2013-09-08T17:19:12.123456+02:00
            2013-09-08 17:19:12
            2013-09-08 17:19:12.123
            2013-09-08 17:19:12.123456
            2013-09-08 17:19:12.123456
            2013-09-08 15:19:12.123
            2013-09-08 15:19:12.123
            """);
    }

    @Test
    void shouldRenderNegativeMillisTimestamp() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            """
                {{ -1234567890123 | date(format="iso_milli", timeZone="UTC") }}
                {{ 1234567890123 | date(format="iso_milli", timeZone="UTC") }}
                """,
            Map.of()
        );

        assertThat(render).isEqualTo("""
            1930-11-18T00:28:29.877Z
            2009-02-13T23:31:30.123Z
            """);
    }

    @Test
    void shouldKeepOffsetWhenNoTimeZoneGiven() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            """
                {{ "2025-08-26T15:00:00+05:45" | date("yyyyMMddHHmmss") }}
                {{ zoned | date("yyyyMMddHHmmss") }}
                """,
            Map.of("zoned", ZonedDateTime.parse("2025-08-26T15:00:00+05:45[Asia/Kathmandu]"))
        );

        assertThat(render).isEqualTo("""
            20250826150000
            20250826150000
            """);
    }

    @Test
    void shouldKeepOffsetInDateAddWhenNoTimeZoneGiven() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ \"2025-08-26T15:00:00+05:45\" | dateAdd(1, \"HOURS\") }}",
            Map.of()
        );

        assertThat(render).isEqualTo("2025-08-26T16:00:00.000000+05:45");
    }

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
