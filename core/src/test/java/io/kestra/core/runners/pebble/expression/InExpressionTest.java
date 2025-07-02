package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class InExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void inList() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("state", State.Type.SUCCESS);

        String render = variableRenderer.render("{{ state in ['SUCCESS', 'WARNING'] }}", vars);

        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ state in ['FAILED', 'KILLED'] }}", vars);

        assertThat(render).isEqualTo("false");
    }
}
