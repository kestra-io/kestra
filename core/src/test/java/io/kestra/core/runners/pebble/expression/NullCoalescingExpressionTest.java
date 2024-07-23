package io.kestra.core.runners.pebble.expression;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class NullCoalescingExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void firstDefined() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ inner.bla ?? block.test.child }}", vars);

        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block.test.child ?? inner.bla }}", vars);

        assertThat(render, is("awesome"));

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ missing ?? missing2 }}", vars);
        });
    }

    @Test
    void firstDefinedEval() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", ImmutableMap.of("test", ImmutableMap.of("child", "awesome")),
            "inner", "test"
        );

        String render = variableRenderer.render("{{ block.test.child ?? null }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[inner].child ?? null }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[missing].child ?? block[inner].child }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ block[missing].child ?? block[missing2].child ?? block[inner].child }}", vars);
        assertThat(render, is("awesome"));

        render = variableRenderer.render("{{ missing ?? block.test.child }}", vars);
        assertThat(render, is("awesome"));

        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ missing ?? missing2 }}", vars);
        });
    }


    @Test
    void emptyObject() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "block", Map.of()
        );

        String render = variableRenderer.render("{{ block ?? 'UNDEFINED' }}", vars);

        assertThat(render, is("{}"));
    }

    @Test
    void nullOrUndefined() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("null", null);

        String render = variableRenderer.render("{{ null ?? 'IS NULL' }}", vars);

        assertThat(render, is("IS NULL"));

        render = variableRenderer.render("{{ undefined ?? 'IS UNDEFINED' }}", vars);

        assertThat(render, is("IS UNDEFINED"));
    }
}