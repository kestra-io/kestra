package io.kestra.core.runners.pebble;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.Rethrow;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class PebbleVariableRendererTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void out() throws IllegalVariableEvaluationException {
        Map<String, Object> in = Map.of(
            "string", "{{ string }}",
            "int", "{{ int }}",
            "float", "{{ float }}",
            "list", "{{ list }}",
            "bool", "{{ bool }}",
            "date", "{{ date }}",
            "map", "{{ map }}",
            "escape", "{{ list }} // {{ map }}",
            "empty", "{{ list[3] is defined ? bla : null }}",
            "concat", "{{ \"apple\" ~ \"pear\" ~ \"banana\" }}"
        );

        Map<String, Object> vars = Map.of(
            "string", "string",
            "int", 1,
            "float", 1.123F,
            "list", Arrays.asList(
                "string",
                1,
                1.123F
            ),
            "bool", true,
            "date", ZonedDateTime.parse("2013-09-08T16:19:00+02"),
            "map", Map.of(
                "string", "string",
                "int", 1,
                "float", 1.123F
            )
        );

        Map<String, Object> render = variableRenderer.render(in, vars);

        assertThat(render.get("string")).isEqualTo("string");
        assertThat(render.get("int")).isEqualTo("1");
        assertThat(render.get("float")).isEqualTo("1.123");
        assertThat(render.get("list")).isEqualTo("[\"string\",1,1.123]");
        assertThat(render.get("bool")).isEqualTo("true");
        assertThat(render.get("date")).isEqualTo("2013-09-08T16:19+02:00");
        assertThat((String) render.get("map")).contains("\"int\":1");
        assertThat((String) render.get("map")).contains("\"int\":1");
        assertThat((String) render.get("map")).contains("\"float\":1.123");
        assertThat((String) render.get("map")).contains("\"string\":\"string\"");
        assertThat((String) render.get("map")).startsWith("{");
        assertThat((String) render.get("map")).endsWith("}");
        assertThat((String) render.get("escape")).contains("[\"string\",1,1.123] // {");
        assertThat((String) render.get("empty")).isEqualTo("");
        assertThat((String) render.get("concat")).isEqualTo("applepearbanana");
    }

    @Test
    void autoJson() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of(
            "map", Map.of("a", "1", "b", "2"),
            "collection", List.of("1","2", "3"),
            "array",  new String[]{"1", "2", "3"},
            "inta",  new Integer[]{1, 2, 3}
        );

        Map<String, Object> in = Map.of(
            "map", "{{ map }}",
            "collection", "{{ collection }}",
            "array",  "{{ array }}",
            "inta",  "{{ inta }}"
        );

        Map<String, Object> render = variableRenderer.render(in, vars);

        assertThat((String) render.get("map")).contains("\"a\":\"1\"");
        assertThat((String) render.get("map")).contains("\"b\":\"2\"");
        assertThat(render.get("collection")).isEqualTo("[\"1\",\"2\",\"3\"]");
        assertThat(render.get("array")).isEqualTo("[\"1\",\"2\",\"3\"]");
        assertThat(render.get("inta")).isEqualTo("[1,2,3]");
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> {
            Rethrow.throwSupplier(() -> {
                variableRenderer.render("{{ missing is defined ? missing : missing2 }}", Map.of());
                return null;
            }).get();
        });
    }

    @Test
    void macro() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{% block \"post\" %} content {% endblock %}{{ block(\"post\") }}",
            Map.of()
        );

        assertThat(render).contains("content");
    }

    @Test
    void numberFormat() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ var | numberFormat(\"#.##\") }}",
            Map.of("var",  1.232654F)
        );

        assertThat(render).contains("1.23");
    }

    @SuppressWarnings("unchecked")
    @Test
    void map() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> in = ImmutableMap.of(
            "string", "{{test}}",
            "list", Arrays.asList(
                "{{test}}",
                "{{test2}}"
            ),
            "int", 1
        );

        ImmutableMap<String, Object> vars = ImmutableMap.of("test", "top", "test2", "awesome");

        Map<String, Object> render = variableRenderer.render(in, vars);

        assertThat(render.get("string")).isEqualTo("top");
        assertThat((List<String>) render.get("list")).containsExactlyInAnyOrder("top", "awesome");
        assertThat(render.get("int")).isEqualTo(1);
    }

    @Test
    void recursive() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", "1",
            "second", "{{first}}",
            "third", "{{second}}",
            "fourth", "{{render(third, recursive=false)}}",
            "map", ImmutableMap.of(
                "third", "{{third}}"
            ),
            "list", ImmutableList.of(
                "{{third}}"
            ),
            "set", ImmutableSet.of(
                "{{third}}"
            )
        );

        String render = variableRenderer.render("{{ third }}", vars);
        assertThat(render).isEqualTo("{{second}}");
        render = variableRenderer.render("{{ render(third, recursive=false) }}", vars);
        assertThat(render).isEqualTo("{{first}}");
        render = variableRenderer.render("{{ render(third) }}", vars);
        assertThat(render).isEqualTo("1");

        // even if recursive = false in the underneath variable, we don't disable recursiveness since it's too hacky and an edge case
        render = variableRenderer.render("{{ render(fourth) }}", vars);
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ map }}", vars);
        assertThat(render).isEqualTo("{\"third\":\"{{third}}\"}");
        render = variableRenderer.render("{{ render(map, recursive=false) }}", vars);
        assertThat(render).isEqualTo("{\"third\":\"{{second}}\"}");
        render = variableRenderer.render("{{ render(map) }}", vars);
        assertThat(render).isEqualTo("{\"third\":\"1\"}");

        render = variableRenderer.render("{{ list }}", vars);
        assertThat(render).isEqualTo("[\"{{third}}\"]");
        render = variableRenderer.render("{{ render(list, recursive=false) }}", vars);
        assertThat(render).isEqualTo("[\"{{second}}\"]");
        render = variableRenderer.render("{{ render(list) }}", vars);
        assertThat(render).isEqualTo("[\"1\"]");

        render = variableRenderer.render("{{ set }}", vars);
        assertThat(render).isEqualTo("[\"{{third}}\"]");
        render = variableRenderer.render("{{ render(set, recursive=false) }}", vars);
        assertThat(render).isEqualTo("[\"{{second}}\"]");
        render = variableRenderer.render("{{ render(set) }}", vars);
        assertThat(render).isEqualTo("[\"1\"]");
    }

    @Test
    void recursiveRenderingAmountLimit() {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", "{{second}}",
            "second", "{{first}}"
        );

        IllegalVariableEvaluationException illegalVariableEvaluationException = assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ render(first) }}", vars)
        );
        assertThat(illegalVariableEvaluationException.getMessage()).contains("Too many rendering attempts");
    }

    @Test
    void raw() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "var", "1"
        );

        String render = variableRenderer.render("See some code {% raw %}{{ var }}{% endraw %}", vars);
        assertThat(render).isEqualTo("See some code {{ var }}");

        render = variableRenderer.render("See some code {%raw%}{{ var }}{%endraw%}", vars);
        assertThat(render).isEqualTo("See some code {{ var }}");

        render = variableRenderer.render("See some code {%-  raw%}{{ var }}{%endraw -%}", vars);
        assertThat(render).isEqualTo("See some code {{ var }}");

        render = variableRenderer.render("See some code {% raw %}{{ var }}{% endraw %} and some other code {% raw %}{{ var2 }}{% endraw %}", vars);
        assertThat(render).isEqualTo("See some code {{ var }} and some other code {{ var2 }}");
    }

    @Test
    void eval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ block[inner].child }}", vars);

        assertThat(render).isEqualTo("awesome");
    }

    @Test
    void firstDefined() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ inner.bla is not defined ? block.test.child : null }}", vars);

        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ block.test.child is defined ? block.test.child : inner.bla }}", vars);

        assertThat(render).isEqualTo("awesome");

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ missing is defined ? missing : missing2 }}", vars);
        });
    }

    @Test
    void firstDefinedEval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ block.test.child is defined ? block.test.child : null }}", vars);
        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ block[inner].child is defined ? block[inner].child : null }}", vars);
        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ block[missing].child is defined ? null : block[inner].child }}", vars);
        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ block[missing].child is not defined ? (block[missing2].child is not defined ? block[inner].child : null) : null }}", vars);
        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ missing is defined ? null : block.test.child }}", vars);
        assertThat(render).isEqualTo("awesome");

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ missing is defined ? missing : missing2 }}", vars);
        });
    }

    @Test
    void get() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ block['test'] }}", vars);
        assertThat(render).isEqualTo("{\"child\":\"awesome\"}");

        render = variableRenderer.render("{{ block['test']['child'] }}", vars);
        assertThat(render).isEqualTo("awesome");

        render = variableRenderer.render("{{ block[inner]['child'] }}", vars);
        assertThat(render).isEqualTo("awesome");

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ get missing }}", vars);
        });
    }

    /**
     * Ensures that we don't erase nested numbers list as there was a bug
     */
    @Test
    void mapWithNestedNumberList() throws IllegalVariableEvaluationException {
        Map<String, Object> map = Map.of(
            "numbers", List.of(1, 2, 3)
        );
        assertThat(variableRenderer.render(map, Map.of())).isEqualTo(map);
    }
}
