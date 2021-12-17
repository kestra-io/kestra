package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
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
            "{{ date | date(format='iso', timeZone='Europe/Paris') }}\n" +
                "{{ instant | date(format=\"iso_sec\", timeZone=\"Europe/Paris\") }}\n" +
                "{{ zoned | date(format=\"iso\", timeZone=\"Europe/Paris\") }}\n" +
                "{{ local | date(format=\"yyyy-MM-dd HH:mm:ss.SSSSSSXXX\", timeZone=\"Europe/Paris\") }}\n" +
                "{{ local | date(format=\"yyyy-MM-dd HH:mm:ss.SSSSSSXXX\", timeZone=\"UTC\") }}",
            vars
        );

        assertThat(render, is("2013-09-08T17:19:12.000000+02:00\n" +
            "2013-09-08T17:19:12+02:00\n" +
            "2013-09-08T17:19:12.123456+02:00\n" +
            "2013-09-08 16:19:12.123456+02:00\n" +
            "2013-09-08 16:19:12.123456Z"
        ));
    }

    @Test
    void timestamp() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestamp(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552"));
    }

    @Test
    void instantnano() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestampNano(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552123456000"));
    }

    @Test
    void instantmicro() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | timestampMicro(timeZone=\"Europe/Paris\") }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552000123456"));
    }

    @Test
    void now() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ now() }}", ImmutableMap.of());
        assertThat(render, containsString(ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));

        render = variableRenderer.render("{{ now(timeZone=\"Europe/Lisbon\") }}", ImmutableMap.of());

        assertThat(render, containsString(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ISO_LOCAL_DATE)));
        assertThat(render, containsString(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ofPattern("HH:mm"))));

        render = variableRenderer.render("{{ now(format=\"iso_local_date\") }}", ImmutableMap.of());

        assertThat(render, is(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ISO_LOCAL_DATE)));
    }

    @Test
    void add() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ zoned | dateAdd(-1,\"DAYS\",timeZone=\"Europe/Paris\")}}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("2013-09-07T17:19:12.123456+02:00"));
    }
}
