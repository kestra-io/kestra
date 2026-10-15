package io.kestra.plugin.core.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.Map;

import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class UnsetVariablesTest {
    @Inject
    RunContextFactory runContextFactory;

    @ExecuteFlow("flows/valids/unset-variables.yaml")
    @Test
    void shouldUpdateExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(((Map<String, Object>) execution.getTaskRunList().get(2).getOutputs().get("values"))).containsEntry("message", "default");
    }
    @Test
    void shouldIgnoreMissingNestedParentWhenIgnoreMissingTrue() throws Exception {
        UnsetVariables task = JacksonMapper.ofJson().readValue(
            """
                {
                  "id": "unset",
                  "type": "io.kestra.plugin.core.execution.UnsetVariables",
                  "ignoreMissing": true,
                  "variables": ["missingParent.child"]
                }""",
            UnsetVariables.class
        );

        Execution execution = task.update(execution(), runContextFactory.of(Map.of()));

        assertThat(execution.getVariables()).containsOnlyKeys("first", "second");
    }

    @Test
    void shouldFailWhenNestedParentMissingAndIgnoreMissingFalse() throws JsonProcessingException {
        UnsetVariables task = JacksonMapper.ofJson().readValue(
            """
                {
                  "id": "unset",
                  "type": "io.kestra.plugin.core.execution.UnsetVariables",
                  "ignoreMissing": false,
                  "variables": ["missingParent.child"]
                }""",
            UnsetVariables.class
        );

        assertThatThrownBy(() -> task.update(execution(), runContextFactory.of(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missingParent");
    }

    private Execution execution() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("first", "1");
        variables.put("second", "2");

        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("unset-variables")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING))
            .variables(variables)
            .build();
    }
}