package io.kestra.core.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class OutputValuesTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @SuppressWarnings("unchecked")
    @Test
    void output() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "output-values");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        TaskRun outputValues = execution.getTaskRunList().getFirst();
        Map<String, Object> values = (Map<String, Object>) outputValues.getOutputs().get("values");
        assertThat(values.get("output1"), is("xyz"));
        assertThat(values.get("output2"), is("abc"));
    }
}
