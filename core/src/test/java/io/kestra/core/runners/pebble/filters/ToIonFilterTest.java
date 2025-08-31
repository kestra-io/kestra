package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class ToIonFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void toIonFilter() throws IllegalVariableEvaluationException {
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

        String render = variableRenderer.render("{{ vars.second.string | toIon }}", vars);
        assertThat(render).isEqualTo("\"string\"");

        render = variableRenderer.render("{{ vars.second.int | toIon }}", vars);
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ vars.second.float | toIon }}", vars);
        assertThat(render).isEqualTo("1.1230000257492065e0");

        render = variableRenderer.render("{{ vars.second.list | toIon }}", vars);
        assertThat(render).isEqualTo("[\"string\",1,1.1230000257492065e0]");

        render = variableRenderer.render("{{ vars.second.bool | toIon }}", vars);
        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ vars.second.date | toIon }}", vars);
        assertThat(render).startsWith("ZonedDateTime::\"" + date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        render = variableRenderer.render("{{ vars.second.map | toIon }}", vars);
        assertThat(render).contains("int:1");
        assertThat(render).contains("float:1.1230000257492065e0");
        assertThat(render).contains("string:\"string\"");
        assertThat(render).startsWith("{");
        assertThat(render).endsWith("}");

        render = variableRenderer.render("{{ {\"empty_object\":{}} | toIon }}", Map.of());
        assertThat(render).isEqualTo("{empty_object:{}}");

        render = variableRenderer.render("{{ null | toIon }}", Map.of());
        assertThat(render).isEqualTo("null");
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ | toIon }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ {not: json} | toIon }}", Map.of()));
    }
}