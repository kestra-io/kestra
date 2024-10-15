package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TaskWithRunIfTest extends AbstractMemoryRunnerTest {

    @Test
    void runnableTask() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-runif");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.findTaskRunsByTaskId("executed").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("notexecuted").getFirst().getState().getCurrent(), is(State.Type.SKIPPED));
        assertThat(execution.findTaskRunsByTaskId("willfailedtheflow").getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }

}
