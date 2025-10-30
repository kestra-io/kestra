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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class JqFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fromString() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ [1, 2, 3] | jq(\".[0]\") | first }}", Map.of());
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ my_vars | jq(\".test[0]\") }}", Map.of(
            "my_vars", Map.of(
                "test", Arrays.asList(1, 2, 3)
            )
        ));
        assertThat(render).isEqualTo("[1]");
    }

    @Test
    void simple() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", ImmutableMap.of("second", ImmutableMap.of("third", "{{third}}")),
            "end", "awesome",
            "third", "{{end}}"
        );

        String render = variableRenderer.render("{{  render(first) | jq(\".second.third\") }}", vars);
        assertThat(render).isEqualTo("[\"awesome\"]");
    }

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

        String render = variableRenderer.render("{{ vars | jq(\".second.string\") }}", vars);
        assertThat(render).isEqualTo("[\"string\"]");

        render = variableRenderer.render("{{ vars | jq(\".second.string\") | first }}", vars);
        assertThat(render).isEqualTo("string");

        render = variableRenderer.render("{{ vars | jq(\".second.int\") | first }}", vars);
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ vars | jq(\".second.float\") | first }}", vars);
        assertThat(render).isEqualTo("1.123");

        render = variableRenderer.render("{{ vars | jq(\".second.list\") | first }}", vars);
        assertThat(render).isEqualTo("[\"string\",1,1.123]");

        render = variableRenderer.render("{{ vars | jq(\".second.bool\") | first }}", vars);
        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ vars | jq(\".second.date\") | first }}", vars);
        assertThat(render).isEqualTo(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        render = variableRenderer.render("{{ vars | jq(\".second.map\") | first }}", vars);
        assertThat(render).contains("\"int\":1");
        assertThat(render).contains("\"int\":1");
        assertThat(render).contains("\"float\":1.123");
        assertThat(render).contains("\"string\":\"string\"");
        assertThat(render).startsWith("{");
        assertThat(render).endsWith("}");
    }

    @Test
    void list() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Arrays.asList(1, 2, 3))
        );

        String render = variableRenderer.render("{{ vars | jq(\".second[]\") }}", vars);
        assertThat(render).isEqualTo("[1,2,3]");
    }

    @Test
    void typed() throws IllegalVariableEvaluationException {
        HashMap<String, Object> value = new HashMap<>();
        value.put("string", "string");
        value.put("int", 1);
        value.put("float", 1.123F);
        value.put("bool", true);
        value.put("null", null);

        ImmutableMap<String, Object> vars = ImmutableMap.of("vars", value);

        assertThat(variableRenderer.render("{{ vars | jq(\".string\") | first | className }}", vars)).isEqualTo("java.lang.String");
        assertThat(variableRenderer.render("{{ vars | jq(\".int\") | first | className }}", vars)).isEqualTo("java.lang.Integer");
        assertThat(variableRenderer.render("{{ vars | jq(\".float\") | first | className }}", vars)).isEqualTo("java.lang.Float");
        assertThat(variableRenderer.render("{{ vars | jq(\".bool\") | first | className }}", vars)).isEqualTo("java.lang.Boolean");
        assertThat(variableRenderer.render("{{ vars | jq(\".null\") | first | className }}", vars)).isEqualTo("");
    }

    @Test
    void object() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of(
                "object", Map.of("key", "value"),
                "array", new String[]{"arrayValue"}
            )
        );

        String render = variableRenderer.render("{% set object = vars | jq(\".object\") %}{{object[0].key}}", vars);
        assertThat(render).isEqualTo("value");

        render = variableRenderer.render("{% set array = vars | jq(\".array\") %}{{array[0][0]}}", vars);
        assertThat(render).isEqualTo("arrayValue");
    }
}
