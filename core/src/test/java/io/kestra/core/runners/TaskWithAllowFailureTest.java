package io.kestra.core.runners;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TaskWithAllowFailureTest extends AbstractMemoryRunnerTest {

    @Test
    void runnableTask() throws TimeoutException, InternalException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-allow-failure-runnable");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("fail").get(0).getAttempts().size(), is(3));
    }
}
