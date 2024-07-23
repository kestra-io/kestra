package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class UndefinedCoalescingExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void nullOrUndefined() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("null", null);

        String render = variableRenderer.render("{{ null ??? 'IS NULL' }}", vars);

        assertThat(render, is(""));

        render = variableRenderer.render("{{ undefined ??? 'IS UNDEFINED' }}", vars);

        assertThat(render, is("IS UNDEFINED"));
    }
}