package org.kestra.core.runners.handlebars.helpers;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.runners.VariableRenderer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class DateHelperTest {
    private final static VariableRenderer VARIABLE_RENDERER = new VariableRenderer();
    public static final ZonedDateTime NOW = ZonedDateTime.parse("2013-09-08T16:19:12.123456+01");

    @Test
    void dateFormat() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "date", new Date(NOW.toEpochSecond() * 1000),
            "instant", NOW.toInstant(),
            "zoned", NOW,
            "local", NOW.toLocalDateTime()
        );

        String render = VARIABLE_RENDERER.render(
            "{{ dateFormat date iso tz=\"Europe/Paris\"}}\n" +
                "{{ dateFormat instant \"iso_sec\" tz=\"Europe/Paris\" }}\n" +
                "{{ dateFormat zoned \"iso\" tz=\"Europe/Paris\" }}\n" +
                "{{ dateFormat local \"yyyy-MM-dd HH:mm:ss.SSSSSSXXX\" tz=\"Europe/Paris\" }}\n" +
                "{{ dateFormat local \"yyyy-MM-dd HH:mm:ss.SSSSSSXXX\" tz=\"UTC\" }}",
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
        String render = VARIABLE_RENDERER.render(
            "{{ timestamp zoned tz=\"Europe/Paris\" }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552"));
    }

    @Test
    void instantnamo() throws IllegalVariableEvaluationException {
        String render = VARIABLE_RENDERER.render(
            "{{ namotimestamp zoned tz=\"Europe/Paris\" }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552123456000"));
    }

    @Test
    void instantmicro() throws IllegalVariableEvaluationException {
        String render = VARIABLE_RENDERER.render(
            "{{ microtimestamp zoned tz=\"Europe/Paris\" }}",
            ImmutableMap.of(
                "zoned", NOW
            )
        );

        assertThat(render, is("1378653552000123456"));
    }

    @Test
    void now() throws IllegalVariableEvaluationException {
        String render = VARIABLE_RENDERER.render("{{ now tz=\"Europe/Lisbon\" }}", ImmutableMap.of());

        assertThat(render, containsString(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ISO_LOCAL_DATE)));
        assertThat(render, containsString(ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).format(DateTimeFormatter.ofPattern("HH:mm"))));
    }
}
