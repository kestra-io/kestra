package org.kestra.core.runners;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ExecutionServiceTest extends AbstractMemoryRunnerTest {
    @Test
    void failedFirst() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "failed-first");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
    }
}