package io.kestra.core.runners.pebble;

import com.google.common.collect.ImmutableMap;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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

        assertThat(render.get("string"), is("string"));
        assertThat(render.get("int"), is("1"));
        assertThat(render.get("float"), is("1.123"));
        assertThat(render.get("list"), is("[\"string\",1,1.123]"));
        assertThat(render.get("bool"), is("true"));
        assertThat(render.get("date"), is("2013-09-08T16:19+02:00"));
        assertThat((String) render.get("map"), containsString("\"int\":1"));
        assertThat((String) render.get("map"), containsString("\"int\":1"));
        assertThat((String) render.get("map"), containsString("\"float\":1.123"));
        assertThat((String) render.get("map"), containsString("\"string\":\"string\""));
        assertThat((String) render.get("map"), startsWith("{"));
        assertThat((String) render.get("map"), endsWith("}"));
        assertThat((String) render.get("escape"), containsString("[\"string\",1,1.123] // {"));
        assertThat((String) render.get("empty"), is(""));
        assertThat((String) render.get("concat"), is("applepearbanana"));
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

        assertThat((String)render.get("map"), containsString("\"a\":\"1\""));
        assertThat((String)render.get("map"), containsString("\"b\":\"2\""));
        assertThat(render.get("collection"), is("[\"1\",\"2\",\"3\"]"));
        assertThat(render.get("array"), is("[\"1\",\"2\",\"3\"]"));
        assertThat(render.get("inta"), is("[1,2,3]"));
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

        assertThat(render, containsString("content"));
    }

    @Test
    void numberFormat() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ var | numberFormat(\"#.##\") }}",
            Map.of("var",  1.232654F)
        );

        assertThat(render, containsString("1.23"));
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

        assertThat(render.get("string"), is("top"));
        assertThat((List<String>) render.get("list"), containsInAnyOrder("top", "awesome"));
        assertThat(render.get("int"), is(1));
    }

    @Test
    void recursive() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", "1",
            "second", "{{third}}",
            "third", "{{first}}"
        );

        String render = variableRenderer.render("{{ second }}", vars);

        assertThat(render, is("1"));
    }

    @Test
    void raw() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "var", "1"
        );

        String render = variableRenderer.render("See some code {% raw %}{{ var }}{% endraw %}", vars);
        assertThat(render, is("See some code {{ var }}"));

        render = variableRenderer.render("See some code {%raw%}{{ var }}{%endraw%}", vars);
        assertThat(render, is("See some code {{ var }}"));

        render = variableRenderer.render("See some code {%-  raw%}{{ var }}{%endraw -%}", vars);
        assertThat(render, is("See some code {{ var }}"));

        render = variableRenderer.render("See some code {% raw %}{{ var }}{% endraw %} and some other code {% raw %}{{ var2 }}{% endraw %}", vars);
        assertThat(render, is("See some code {{ var }} and some other code {{ var2 }}"));
    }

    @Test
    void eval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ block[inner].child }}", vars);

        assertThat(render, is("awesome"));
    }

    @Test
    void firstDefined() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ inner.bla is not defined ? block.test.child : null }}", vars);

        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block.test.child is defined ? block.test.child : inner.bla }}", vars);

        assertThat(render, is("awesome"));

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
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[inner].child is defined ? block[inner].child : null }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[missing].child is defined ? null : block[inner].child }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[missing].child is not defined ? (block[missing2].child is not defined ? block[inner].child : null) : null }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ missing is defined ? null : block.test.child }}", vars);
        assertThat(render, is("awesome"));

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
        assertThat(render, is("{\"child\":\"awesome\"}"));

        render = variableRenderer.render("{{ block['test']['child'] }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[inner]['child'] }}", vars);
        assertThat(render, is("awesome"));

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ get missing }}", vars);
        });
    }
}
