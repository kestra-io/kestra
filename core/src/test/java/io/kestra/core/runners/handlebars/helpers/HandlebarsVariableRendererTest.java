package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.HandlebarsException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(environments = "handlebars")
class HandlebarsVariableRendererTest {
    @Inject
    VariableRenderer variableRenderer;

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
            "second", "{{first}}",
            "third", "{{second}}",
            "fourth", "{{render(third, recursive=false)}}"
        );

        String render = variableRenderer.render("{{ third }}", vars);
        assertThat(render, is("{{second}}"));

        render = variableRenderer.render("{{ render(third) }}", vars);
        assertThat(render, is("1"));

        // even if recursive = false in the underneath variable, we don't disable recursiveness since it's too hacky and an edge case
        render = variableRenderer.render("{{ render(fourth) }}", vars);
        assertThat(render, is("1"));
    }

    @Test
    void eval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ eval 'block.[{{inner}}].child' }}", vars);

        assertThat(render, is("awesome"));
    }

    @Test
    void firstDefined() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ firstDefined inner.bla block.test.child }}", vars);

        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ firstDefined block.test.child inner.bla }}", vars);

        assertThat(render, is("awesome"));


        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ firstDefined missing missing2 }}", vars);
        });
    }

    @Test
    void firstDefinedEval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ firstDefinedEval 'block.test.child' 'missing' }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ firstDefinedEval 'missing' 'block.test.child' }}", vars);
        assertThat(render, is("awesome"));

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ firstDefinedEval 'missing' 'missing2' }}", vars);
        });
    }

    @Test
    void get() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ get block 'test' }}", vars);
        assertThat(render, is("{child=awesome}"));

        render = variableRenderer.render("{{ get (get block 'test') 'child' }}", vars);
        assertThat(render, is("awesome"));

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ get missing }}", vars);
        });
    }

    @Test
    void substringBefore() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ substringBefore 'a.b.c' '.' }}", Map.of());
        assertThat(render, is("a"));
    }

    @Test
    void substringAfter() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ substringAfter 'a.b.c' '.' }}", Map.of());
        assertThat(render, is("b.c"));
    }

    @Test
    void substringBeforeLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ substringBeforeLast 'a.b.c' '.' }}", Map.of());
        assertThat(render, is("a.b"));
    }

    @Test
    void substringAfterLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ substringAfterLast 'a.b.c' '.' }}", Map.of());
        assertThat(render, is("c"));
    }
}
