package io.kestra.core.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class FetchTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void fetch() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "get-log");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
        TaskRun fetch = execution.getTaskRunList().get(2);
        assertThat(fetch.getOutputs().get("size"), is(2));
    }

    @Test
    void fetchWithTaskId() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "get-log-taskid");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
        TaskRun fetch = execution.getTaskRunList().get(2);
        assertThat(fetch.getOutputs().get("size"), is(1));
    }

    @Test
    void fetchWithExecutionId() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "get-log-executionid");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
        TaskRun fetch = execution.getTaskRunList().get(2);
        assertThat(fetch.getOutputs().get("size"), is(2));
    }
}
