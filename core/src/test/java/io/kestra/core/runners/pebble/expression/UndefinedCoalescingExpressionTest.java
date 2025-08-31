package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class UndefinedCoalescingExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void nullOrUndefined() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("null", null);

        String render = variableRenderer.render("{{ null ??? 'IS NULL' }}", vars);

        assertThat(render).isEqualTo("");

        render = variableRenderer.render("{{ undefined ??? 'IS UNDEFINED' }}", vars);

        assertThat(render).isEqualTo("IS UNDEFINED");
    }
}