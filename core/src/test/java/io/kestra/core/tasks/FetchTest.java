package io.kestra.core.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class FetchTest extends AbstractMemoryRunnerTest {
    @Test
    void fetch() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "get-log");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(4));
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size"), is(3));
    }

    @Test
    void fetchWithTaskId() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "get-log-taskid");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(4));
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size"), is(1));
    }

    @Test
    void fetchWithExecutionId() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "get-log-executionid");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(4));
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size"), is(3));
    }
}
