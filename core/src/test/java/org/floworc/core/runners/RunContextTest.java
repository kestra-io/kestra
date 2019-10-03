package org.floworc.core.runners;

import org.floworc.core.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class RunContextTest extends AbstractMemoryRunnerTest {
    @Test
    void logs() throws TimeoutException {
        Execution execution = runnerUtils.runOne("logs");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(execution.getTaskRunList().get(0).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getLogs().get(0).getLevel(), is(Level.TRACE));
        assertThat(execution.getTaskRunList().get(0).getLogs().get(0).getMessage(), is("first {{todo}}"));

        assertThat(execution.getTaskRunList().get(1).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(1).getLogs().get(0).getLevel(), is(Level.WARN));

        assertThat(execution.getTaskRunList().get(2).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getLogs().get(0).getLevel(), is(Level.ERROR));
    }
}