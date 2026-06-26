package io.kestra.core.runners.pebble.functions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class TasksWithStateFunctionTest {
    @Inject
    private VariableRenderer variableRenderer;

    private Map<String, Object> tasksVar(Object helloState) {
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("hello", Map.of("state", helloState));
        return Map.of("tasks", tasks);
    }

    // state as a plain String — how worker-side variables look after JSON serialization.
    @Test
    void shouldMatchWhenStateIsString() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = tasksVar("SUCCESS");

        assertThat(variableRenderer.render("{{ tasksWithState(state='SUCCESS') is empty }}", vars)).isEqualTo("false");
        assertThat(variableRenderer.render("{{ tasksWithState(state='SUCCESS') is not empty }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ tasksWithState(state='FAILED') is empty }}", vars)).isEqualTo("true");
    }

    // state as a State.Type enum — how the executor renders runIf (computeTasksMap stores the enum).
    // Regression for Pylon #1984: tasksWithState() must behave the same as the String case.
    @Test
    void shouldMatchWhenStateIsEnum() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = tasksVar(State.Type.SUCCESS);

        assertThat(variableRenderer.render("{{ tasksWithState(state='SUCCESS') is empty }}", vars)).isEqualTo("false");
        assertThat(variableRenderer.render("{{ tasksWithState(state='SUCCESS') is not empty }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ tasksWithState(state='FAILED') is empty }}", vars)).isEqualTo("true");
    }

    // task-with-value (loop / iteration): tasks.<id>.<value> = { state: <enum> } — nested branch.
    @Test
    void shouldMatchNestedValueWhenStateIsEnum() throws IllegalVariableEvaluationException {
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("loop", Map.of("item-1", Map.of("state", State.Type.FAILED)));
        Map<String, Object> vars = Map.of("tasks", tasks);

        assertThat(variableRenderer.render("{{ tasksWithState(state='FAILED') is not empty }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ tasksWithState(state='SUCCESS') is empty }}", vars)).isEqualTo("true");
    }
}
