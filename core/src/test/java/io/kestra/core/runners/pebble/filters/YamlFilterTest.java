package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class YamlFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void map() throws IllegalVariableEvaluationException {

        ZonedDateTime date = ZonedDateTime.parse("2013-09-08T16:19:00+02").withZoneSameLocal(ZoneId.systemDefault());

        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Map.of(
                "string", "string",
                "int", 1,
                "float", 1.123F,
                "list", Arrays.asList(
                    "string",
                    1,
                    1.123F
                ),
                "bool", true,
                "date", date,
                "map", Map.of(
                    "string", "string",
                    "int", 1,
                    "float", 1.123F
                )
            ))
        );

        String render = variableRenderer.render("{{ vars.second.string | yaml }}", vars);
        assertThat(render, is("\"string\""));

        render = variableRenderer.render("{{ vars.second.int | yaml }}", vars);
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ vars.second.float | yaml }}", vars);
        assertThat(render, is("1.123"));

        render = variableRenderer.render("{{ vars.second.list | yaml }}", vars);
        assertThat(render, is("[\"string\",1,1.123]"));

        render = variableRenderer.render("{{ vars.second.bool | yaml }}", vars);
        assertThat(render, is("true"));

        render = variableRenderer.render("{{ vars.second.date | yaml }}", vars);
        assertThat(render, is("\"" + date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\""));

        render = variableRenderer.render("{{ vars.second.map | yaml }}", vars);
        assertThat(render, containsString("\"int\":1"));
        assertThat(render, containsString("\"int\":1"));
        assertThat(render, containsString("\"float\":1.123"));
        assertThat(render, containsString("\"string\":\"string\""));
        assertThat(render, startsWith("{"));
        assertThat(render, endsWith("}"));
    }
}
