package org.kestra.core.runners;

import com.github.jknack.handlebars.HandlebarsException;
import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class VariableRendererTest {
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
            "second", "{{third}}",
            "third", "{{first}}"
        );

        String render = variableRenderer.render("{{ second }}", vars);

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


        assertThrows(HandlebarsException.class, () -> {
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

        assertThrows(HandlebarsException.class, () -> {
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

        assertThrows(HandlebarsException.class, () -> {
            variableRenderer.render("{{ get missing }}", vars);
        });
    }
}
