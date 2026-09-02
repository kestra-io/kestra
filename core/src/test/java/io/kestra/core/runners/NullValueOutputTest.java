package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

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
 * A null nested inside an output must reach the outputs as an explicit null, not as an absent key.
 *
 * @see <a href="https://github.com/kestra-io/plugin-transform/issues/110">plugin-transform#110</a>
 */
@KestraTest(startRunner = true)
class NullValueOutputTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @ExecuteFlow("flows/valids/null-value-output.yaml")
    void shouldKeepNullValuesNestedInOutputs(Execution execution) throws InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.getTaskRunList().getFirst());

        @SuppressWarnings("unchecked")
        List<Object> records = (List<Object>) outputs.get("records");
        assertThat(records).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) records.getFirst();
        assertThat(record).containsEntry("a", 1);
        assertThat(record).containsKey("b");
        assertThat(record.get("b")).isNull();
    }
}
