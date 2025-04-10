package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ToJsonFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void toJsonFilter() throws IllegalVariableEvaluationException {
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

        String render = variableRenderer.render("{{ vars.second.string | toJson }}", vars);
        assertThat(render).isEqualTo("\"string\"");

        render = variableRenderer.render("{{ vars.second.int | toJson }}", vars);
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ vars.second.float | toJson }}", vars);
        assertThat(render).isEqualTo("1.123");

        render = variableRenderer.render("{{ vars.second.list | toJson }}", vars);
        assertThat(render).isEqualTo("[\"string\",1,1.123]");

        render = variableRenderer.render("{{ vars.second.bool | toJson }}", vars);
        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ vars.second.date | toJson }}", vars);
        assertThat(render).isEqualTo("\"" + date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\"");

        render = variableRenderer.render("{{ vars.second.map | toJson }}", vars);
        assertThat(render).contains("\"int\":1");
        assertThat(render).contains("\"int\":1");
        assertThat(render).contains("\"float\":1.123");
        assertThat(render).contains("\"string\":\"string\"");
        assertThat(render).startsWith("{");
        assertThat(render).endsWith("}");

        render = variableRenderer.render("{{ {\"empty_object\":{}} | toJson }}", Map.of());
        assertThat(render).isEqualTo("{\"empty_object\":{}}");

        render = variableRenderer.render("{{ null | toJson }}", Map.of());
        assertThat(render).isEqualTo("null");
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ | toJson }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ {not: json} | toJson }}", Map.of()));
    }

    @Test
    void jsonFilter() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Map.of(
                "string", "string"
            ))
        );

        String render = variableRenderer.render("{{ vars.second.string | json }}", vars);
        assertThat(render).isEqualTo("\"string\"");
    }
}
