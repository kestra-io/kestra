package io.kestra.core.runners.pebble.filters;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class SubstringFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void substringBefore() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBefore('.') }}", Map.of());
        assertThat(render).isEqualTo("a");
    }

    @Test
    void substringAfter() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfter(separator='.') }}", Map.of());
        assertThat(render).isEqualTo("b.c");
    }

    @Test
    void substringBeforeLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast(separator='.') }}", Map.of());
        assertThat(render).isEqualTo("a.b");

        render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast('.') }}", Map.of());
        assertThat(render).isEqualTo("a.b");
    }

    @Test
    void substringAfterLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfterLast('.') }}", Map.of());
        assertThat(render).isEqualTo("c");
    }

    @Test
    void shouldRenderSubstringWhenSeparatorIsNotAString() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ 'a1b1c' | substringAfter(separator=1) }}", Map.of())).isEqualTo("b1c");
        assertThat(variableRenderer.render("{{ 'a1b1c' | substringBefore(separator=1) }}", Map.of())).isEqualTo("a");
        assertThat(variableRenderer.render("{{ 'a1b1c' | substringAfterLast(separator=1) }}", Map.of())).isEqualTo("c");
        assertThat(variableRenderer.render("{{ 'a1b1c' | substringBeforeLast(separator=1) }}", Map.of())).isEqualTo("a1b");

        String render = variableRenderer.render("{{ 'a1b1c' | substringAfter(separator=sep) }}", Map.of("sep", 1));
        assertThat(render).isEqualTo("b1c");
    }

    @Test
    void shouldThrowWhenSeparatorIsNull() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'a1b1c' | substringAfter(separator=null) }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("expects an argument 'separator'");
    }
}
