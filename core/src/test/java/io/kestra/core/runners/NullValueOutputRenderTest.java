package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the reported symptom: a null nested in an output made a downstream expression fail with a
 * missing-variable error, because the key was absent rather than null.
 *
 * @see <a href="https://github.com/kestra-io/plugin-transform/issues/110">plugin-transform#110</a>
 */
@KestraTest(startRunner = true)
class NullValueOutputRenderTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @ExecuteFlow("flows/valids/null-value-output-render.yaml")
    void shouldRenderNullNestedInOutputsAsEmpty(Execution execution) throws InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        var nullRun = execution.findTaskRunsByTaskId("render_null").getFirst();
        assertThat(taskOutputService.getOutputs(nullRun)).containsEntry("value", "[]");

        var siblingRun = execution.findTaskRunsByTaskId("render_sibling").getFirst();
        assertThat(taskOutputService.getOutputs(siblingRun)).containsEntry("value", "[1]");
    }
}
