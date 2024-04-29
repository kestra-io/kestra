package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class InExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldReturnTrueGivenInExprWithValidLeftOpAndRightCollection() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'foo' in value }}", Map.of("value", List.of("foo", "bar")));
        assertThat(render, is("true"));
    }

    @Test
    void shouldReturnTrueGivenInExprWithValidLeftOpAndRightMap() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'foo' in value }}", Map.of("value", Map.of("foo", "bar")));
        assertThat(render, is("true"));
    }

    @Test
    void shouldReturnFalseGivenInExprWithWrongLeftOpAndRightCollection() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'baz' in value }}",  Map.of("value", List.of("foo", "bar")));
        assertThat(render, is("false"));
    }

    @Test
    void shouldReturnFalseGivenInExprWithValidLeftOpAndRightMap() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'foo' in value }}", Map.of("value", Map.of("foo", "bar")));
        assertThat(render, is("true"));
    }

    @Test
    void shouldReturnFalseGivenInExprWithWrongLeftOpAndRightArray() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'baz' in value }}",  Map.of("value", new String[]{"foo", "bar"}));
        assertThat(render, is("false"));
    }

    @Test
    void shouldReturnFalseGivenInExprWithValidLeftOpAndRightArray() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'foo' in value }}",Map.of("value", new String[]{"foo", "bar"}));
        assertThat(render, is("true"));
    }

}
