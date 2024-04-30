package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class WaitForTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void waitfor() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().getFirst().getOutputs(), notNullValue());
    }
    @Test
    void waitforMaxIterations() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-max-iterations");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
    @Test
    void waitforMaxDuration() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-max-duration");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
    @Test
    void waitforNoSuccess() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-no-success");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().getFirst().getOutputs(), nullValue());
    }
}
