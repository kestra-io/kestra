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
 * The two ways a null reaches the outputs, which Jackson treats separately.
 *
 * @see io.kestra.core.serializers.JacksonMapper#toMapKeepingNullValues(Object)
 */
@KestraTest(startRunner = true)
class NullOutputTest {
    @Inject
    private TaskOutputService taskOutputService;

    /** A null bean <em>property</em>, kept only because the output declares {@code @JsonInclude(ALWAYS)}. */
    @Test
    @ExecuteFlow("flows/valids/null-output.yaml")
    void shouldIncludeNullOutput(Execution execution) throws InternalException {
        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).hasSize(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).containsKey("value")).isTrue();
    }

    /**
     * A null as map <em>content</em>, which must stay an explicit null rather than become an absent key.
     *
     * @see <a href="https://github.com/kestra-io/plugin-transform/issues/110">plugin-transform#110</a>
     */
    @Test
    @ExecuteFlow("flows/valids/null-content-output.yaml")
    void shouldKeepNullsNestedInOutputs(Execution execution) throws InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("produce").getFirst());

        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) ((List<Object>) outputs.get("records")).getFirst();
        assertThat(record).containsEntry("a", 1);
        assertThat(record).containsKey("b");
        assertThat(record.get("b")).isNull();

        assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("render_null").getFirst()))
            .containsEntry("value", "[]");
        assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("render_sibling").getFirst()))
            .containsEntry("value", "[1]");
    }
}
