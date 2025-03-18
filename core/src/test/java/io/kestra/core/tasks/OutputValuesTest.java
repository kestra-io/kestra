package io.kestra.core.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class OutputValuesTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/output-values.yml")
    void output(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        TaskRun outputValues = execution.getTaskRunList().getFirst();
        Map<String, Object> values = (Map<String, Object>) outputValues.getOutputs().get("values");
        assertThat(values.get("output1"), is("xyz"));
        assertThat(values.get("output2"), is("abc"));
    }
}
