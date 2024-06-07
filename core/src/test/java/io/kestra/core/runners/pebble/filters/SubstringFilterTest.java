package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class SubstringFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void substringBefore() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBefore('.') }}", Map.of());
        assertThat(render, is("a"));
    }

    @Test
    void substringAfter() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfter(separator='.') }}", Map.of());
        assertThat(render, is("b.c"));
    }

    @Test
    void substringBeforeLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast(separator='.') }}", Map.of());
        assertThat(render, is("a.b"));

        render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast('.') }}", Map.of());
        assertThat(render, is("a.b"));
    }

    @Test
    void substringAfterLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfterLast('.') }}", Map.of());
        assertThat(render, is("c"));
    }
}
