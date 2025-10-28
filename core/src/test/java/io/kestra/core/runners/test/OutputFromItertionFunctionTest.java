package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class OutputFromIterationFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fromListOfOutputs() throws IllegalVariableEvaluationException {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "iteration_0", "result", "first"),
            Map.of("value", "iteration_1", "result", "second"),
            Map.of("value", "iteration_2", "result", "third")
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        String render0 = variableRenderer.render("{{ outputFromIteration(outputs, 0).result }}", vars);
        assertThat(render0).isEqualTo("first");
        
        String render1 = variableRenderer.render("{{ outputFromIteration(outputs, 1).result }}", vars);
        assertThat(render1).isEqualTo("second");
        
        String render2 = variableRenderer.render("{{ outputFromIteration(outputs, 2).result }}", vars);
        assertThat(render2).isEqualTo("third");
    }

    @Test
    void accessPreviousIteration() throws IllegalVariableEvaluationException {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "s1", "result", 10),
            Map.of("value", "s2", "result", 20),
            Map.of("value", "s3", "result", 30)
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        vars.put("currentIteration", 2);
        
        String render = variableRenderer.render("{{ outputFromIteration(outputs, currentIteration - 1).result }}", vars);
        assertThat(render).isEqualTo("20");
    }

    @Test
    void withMapOfOutputs() throws IllegalVariableEvaluationException {
        Map<String, Object> output0 = new HashMap<>();
        output0.put("result", "first_output");
        
        Map<String, Object> output1 = new HashMap<>();
        output1.put("result", "second_output");
        
        Map<String, Object> output2 = new HashMap<>();
        output2.put("result", "third_output");
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("0", output0);
        outputs.put("1", output1);
        outputs.put("2", output2);
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        String render0 = variableRenderer.render("{{ outputFromIteration(outputs, 0).result }}", vars);
        assertThat(render0).isEqualTo("first_output");
        
        String render1 = variableRenderer.render("{{ outputFromIteration(outputs, 1).result }}", vars);
        assertThat(render1).isEqualTo("second_output");
        
        String render2 = variableRenderer.render("{{ outputFromIteration(outputs, 2).result }}", vars);
        assertThat(render2).isEqualTo("third_output");
    }

    @Test
    void outOfBoundsThrowsException() {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "s1"),
            Map.of("value", "s2")
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        assertThatThrownBy(() -> variableRenderer.render("{{ outputFromIteration(outputs, 5) }}", vars))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("out of bounds");
    }

    @Test
    void mapKeyNotFoundThrowsException() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("0", Map.of("value", "s1"));
        outputs.put("1", Map.of("value", "s2"));
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        assertThatThrownBy(() -> variableRenderer.render("{{ outputFromIteration(outputs, 5) }}", vars))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("not found in outputs");
    }

    @Test
    void negativeIndexThrowsException() {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "s1")
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        assertThatThrownBy(() -> variableRenderer.render("{{ outputFromIteration(outputs, -1) }}", vars))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("out of bounds");
    }

    @Test
    void missingOutputsArgumentThrowsException() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("iteration", 0);
        
        assertThatThrownBy(() -> variableRenderer.render("{{ outputFromIteration(iteration: iteration) }}", vars))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("expects an argument 'outputs'");
    }

    @Test
    void missingIterationArgumentThrowsException() {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "s1")
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        
        assertThatThrownBy(() -> variableRenderer.render("{{ outputFromIteration(outputs) }}", vars))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("expects an argument 'iteration'");
    }

    @Test
    void withStringIteration() throws IllegalVariableEvaluationException {
        List<Map<String, Object>> outputs = List.of(
            Map.of("value", "first"),
            Map.of("value", "second")
        );
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        vars.put("iterationStr", "1");
        
        String render = variableRenderer.render("{{ outputFromIteration(outputs, iterationStr).value }}", vars);
        assertThat(render).isEqualTo("second");
    }

    @Test
    void conditionalAccessToPreviousIteration() throws IllegalVariableEvaluationException {
        List<Map<String, Object>> outputs = new ArrayList<>();
        outputs.add(Map.of("value", "s1", "result", 100));
        outputs.add(Map.of("value", "s2", "result", 200));
        outputs.add(Map.of("value", "s3", "result", 300));
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("outputs", outputs);
        vars.put("currentIteration", 0);
        
        String render = variableRenderer.render(
            "{% if currentIteration > 0 %}{{ outputFromIteration(outputs, currentIteration - 1).result }}{% else %}No previous iteration{% endif %}",
            vars
        );
        assertThat(render).isEqualTo("No previous iteration");
        
        vars.put("currentIteration", 2);
        render = variableRenderer.render(
            "{% if currentIteration > 0 %}{{ outputFromIteration(outputs, currentIteration - 1).result }}{% else %}No previous iteration{% endif %}",
            vars
        );
        assertThat(render).isEqualTo("200");
    }
}
