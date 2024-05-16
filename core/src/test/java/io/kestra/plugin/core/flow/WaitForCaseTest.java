package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class WaitForCaseTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    protected RunnerUtils runnerUtils;

    public void waitfor() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().getFirst().getOutputs(), notNullValue());
    }

    public void waitforMaxIterations() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-max-iterations");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void waitforMaxDuration() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-max-duration");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void waitforNoSuccess() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-no-success");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void waitforMultipleTasks() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-multiple-tasks");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Map<String,Object> values = (Map<String, Object>) execution.getTaskRunList().getLast().getOutputs().get("values");
        assertThat(values.get("count"), is("4"));
    }

    public void waitforMultipleTasksFailed() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "waitfor-multiple-tasks-failed");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().getLast().attemptNumber(), is(1));
    }
}
