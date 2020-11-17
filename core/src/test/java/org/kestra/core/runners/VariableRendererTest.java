package org.kestra.core.runners;

import com.github.jknack.handlebars.HandlebarsException;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariableRendererTest {
    private final static VariableRenderer VARIABLE_RENDERER = new VariableRenderer();

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

        Map<String, Object> render = VARIABLE_RENDERER.render(in, vars);

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

        String render = VARIABLE_RENDERER.render("{{ second }}", vars);

        assertThat(render, is("1"));
    }

    @Test
    void eval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = VARIABLE_RENDERER.render("{{ eval 'block.[{{inner}}].child' }}", vars);

        assertThat(render, is("awesome"));
    }

    @Test
    void firstDefined() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = VARIABLE_RENDERER.render("{{ firstDefined inner.bla block.test.child }}", vars);

        assertThat(render, is("awesome"));

        assertThrows(HandlebarsException.class, () -> {
            VARIABLE_RENDERER.render("{{ firstDefined missing missing2 }}", vars);
        });
    }

    @Test
    void firstDefinedEval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = VARIABLE_RENDERER.render("{{ firstDefinedEval 'block.test.child' 'missing' }}", vars);
        assertThat(render, is("awesome"));

        render = VARIABLE_RENDERER.render("{{ firstDefinedEval 'missing' 'block.test.child' }}", vars);
        assertThat(render, is("awesome"));

        assertThrows(HandlebarsException.class, () -> {
            VARIABLE_RENDERER.render("{{ firstDefinedEval 'missing' 'missing2' }}", vars);
        });
    }
}
