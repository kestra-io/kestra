package org.floworc.core.runners;

import org.floworc.core.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ExecutionServiceTest extends AbstractMemoryRunnerTest {
    @Test
    void failedFirst() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.floworc.tests", "failed-first");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
    }
}