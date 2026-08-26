package io.kestra.plugin.core.execution;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class SetVariablesTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private RunContextFactory runContextFactory;

    @ExecuteFlow("flows/valids/set-variables.yaml")
    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateExecution(Execution execution) throws io.kestra.core.exceptions.InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(((Map<String, Object>) taskOutputService.getOutputs(execution.getTaskRunList().get(1)).get("values"))).containsEntry("message", "Hello Loïc");
    }

    @ExecuteFlow("flows/valids/set-variables-duplicate.yaml")
    @Test
    void shouldFailWhenExistingVariable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldRenderVariablesForEachExecution() throws Exception {
        // the executor calls update() on the task instance of its cached flow, so the same task
        // instance is reused by every execution of the flow: it must not reuse the first rendering
        SetVariables task = JacksonMapper.ofJson().readValue(
            """
                {
                  "id": "set",
                  "type": "io.kestra.plugin.core.execution.SetVariables",
                  "variables": {"isLts": "{{ inputs.version | startsWith('1.3.') }}"}
                }""",
            SetVariables.class
        );

        Execution first = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("version", "1.3.9"))));
        assertThat(first.getVariables()).containsEntry("isLts", "true");

        Execution second = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("version", "1.0.56"))));
        assertThat(second.getVariables()).containsEntry("isLts", "false");
    }

    private Execution execution() {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("set-variables")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING))
            .build();
    }
}
