package io.kestra.core.runners.pebble.expression;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class InExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void inList() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("state", State.Type.SUCCESS);

        String render = variableRenderer.render("{{ state isIn ['SUCCESS', 'WARNING'] }}", vars);

        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ state isIn ['FAILED', 'KILLED'] }}", vars);

        assertThat(render).isEqualTo("false");
    }

    @Test
    void inListWithNullElement() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("state", State.Type.SUCCESS);

        String render = variableRenderer.render("{{ state isIn ['SUCCESS', null] }}", vars);

        assertThat(render).isEqualTo("true");
    }

    @Test
    void inMap() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = new HashMap<>();
        vars.put("map", Map.of("key", "value"));
        vars.put("key", "key");
        vars.put("value", "value");

        String render = variableRenderer.render("{{ key isIn map }}", vars);

        assertThat(render).isEqualTo("true");

        render = variableRenderer.render("{{ value isIn map }}", vars);

        assertThat(render).isEqualTo("true");
    }

    @Test
    void inMapWithNullValue() throws IllegalVariableEvaluationException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", null);
        map.put("other", "match");

        Map<String, Object> vars = new HashMap<>();
        vars.put("map", map);
        vars.put("present", "match");
        vars.put("absent", "absent");

        assertThat(variableRenderer.render("{{ present isIn map }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ absent isIn map }}", vars)).isEqualTo("false");
    }

}
