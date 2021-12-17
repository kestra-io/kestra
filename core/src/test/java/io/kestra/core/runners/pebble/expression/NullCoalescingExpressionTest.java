package io.kestra.core.runners.pebble.expression;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
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
}