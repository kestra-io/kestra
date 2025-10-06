package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import jakarta.inject.Inject;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

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

        render = variableRenderer.render("{{ now(format=\"iso_local_date\") }}", ImmutableMap.of());

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
    String render =
        variableRenderer.render(
            """
                {{ 1378653552 | date(format="iso_sec", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="iso_milli", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="iso_zoned_date_time", timeZone="Europe/Paris") }}
                {{ 1378653552123456000 | date(format="iso", timeZone="Europe/Paris") }}
                {{ 1378653552000123456 | date(format="iso", timeZone="Europe/Paris") }}
                {{ 1378653552 | date(format="sql_sec", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="sql_milli", timeZone="Europe/Paris") }}
                {{ 1378653552123456000 | date(format="sql", timeZone="Europe/Paris") }}
                {{ 1378653552000123456 | date(format="sql", timeZone="Europe/Paris") }}
                {{ 1378653552123 | date(format="sql_milli", timeZone="UTC") }}
                {{ "1378653552123" | number | date(format="sql_milli", timeZone="UTC") }}
                """,
            Map.of());

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

}
