package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
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
        assertThat(render).isEqualTo("string\n");

        render = variableRenderer.render("{{ vars.second.int | yaml }}", vars);
        assertThat(render).isEqualTo("1\n");

        render = variableRenderer.render("{{ vars.second.float | yaml }}", vars);
        assertThat(render).isEqualTo("1.123\n");

        render = variableRenderer.render("{{ vars.second.list | yaml }}", vars);
        assertThat(render).isEqualTo(" - string\n - 1\n - 1.123\n");

        render = variableRenderer.render("{{ vars.second.bool | yaml }}", vars);
        assertThat(render).isEqualTo("true\n");

        render = variableRenderer.render("{{ vars.second.date | yaml }}", vars);
        assertThat(render).isEqualTo(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\n");

        render = variableRenderer.render("{{ vars.second.map | yaml }}", vars);
        assertThat(render).contains("int: 1\n");
        assertThat(render).contains("int: 1\n");
        assertThat(render).contains("float: 1.123\n");
        assertThat(render).contains("string: string\n");
    }
}
