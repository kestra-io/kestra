package io.kestra.core.tasks;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.micronaut.context.annotation.Property;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class OutputValuesTest {

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/output-values.yml")
    void output(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        TaskRun outputValues = execution.getTaskRunList().getFirst();
        assertThat(outputValues.getOutputs()).isInstanceOf(Variables.InMemoryVariables.class);
        Map<String, Object> values = (Map<String, Object>) outputValues.getOutputs().get("values");
        assertThat(values.get("output1")).isEqualTo("xyz");
        assertThat(values.get("output2")).isEqualTo("abc");

        outputValues = execution.getTaskRunList().getLast();
        assertThat(outputValues.getOutputs()).isInstanceOf(Variables.InMemoryVariables.class);
        values = (Map<String, Object>) outputValues.getOutputs().get("values");
        assertThat(values.get("output1")).isEqualTo("xyz");
        assertThat(values.get("output2")).isEqualTo("abc");
    }
}
