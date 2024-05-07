package io.kestra.core.tasks.executions;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ResumeTest extends AbstractMemoryRunnerTest {
    @Test
    void resume() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "resume-execution");

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}