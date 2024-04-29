package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class NotInExpressionTest {
    @Inject
    VariableRenderer variableRenderer;


    @Test
    void shouldReturnFalseGivenNotInExprWithValidLeftOp() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'foo' not in ['foo', 'bar'] }}", Map.of());
        assertThat(render, is("false"));
    }

    @Test
    void shouldReturnTrueGivenNotInExprWithWrongLeftOp() throws IllegalVariableEvaluationException {

        String render;
        render = variableRenderer.render("{{ 'baz' not in ['foo', 'bar'] }}", Map.of());
        assertThat(render, is("true"));
    }
}
