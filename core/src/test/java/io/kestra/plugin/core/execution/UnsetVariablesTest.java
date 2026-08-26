package io.kestra.plugin.core.execution;

import java.util.HashMap;
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
class UnsetVariablesTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private RunContextFactory runContextFactory;

    @ExecuteFlow("flows/valids/unset-variables.yaml")
    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateExecution(Execution execution) throws io.kestra.core.exceptions.InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(((Map<String, Object>) taskOutputService.getOutputs(execution.getTaskRunList().get(2)).get("values"))).containsEntry("message", "default");
    }

    @Test
    void shouldRenderVariablesForEachExecution() throws Exception {
        // the executor calls update() on the task instance of its cached flow, so the same task
        // instance is reused by every execution of the flow: it must not reuse the first rendering
        UnsetVariables task = JacksonMapper.ofJson().readValue(
            """
                {
                  "id": "unset",
                  "type": "io.kestra.plugin.core.execution.UnsetVariables",
                  "variables": ["{{ inputs.toUnset }}"]
                }""",
            UnsetVariables.class
        );

        Execution first = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("toUnset", "first"))));
        assertThat(first.getVariables()).containsOnlyKeys("second");

        Execution second = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("toUnset", "second"))));
        assertThat(second.getVariables()).containsOnlyKeys("first");
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
