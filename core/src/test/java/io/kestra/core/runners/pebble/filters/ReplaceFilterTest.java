package io.kestra.core.runners.pebble.filters;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ReplaceFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void string() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'john doe is john doe' | replace({'john': 'jane'}) }}", Map.of());

        assertThat(render).isEqualTo("jane doe is jane doe");
    }

    @Test
    void regexp() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'aa1bb2cc3dd4ee5' | replace({'(\\d)': '-$1-'}, regexp=true) }}", Map.of());

        assertThat(render).isEqualTo("aa-1-bb-2-cc-3-dd-4-ee-5-");
    }
}
