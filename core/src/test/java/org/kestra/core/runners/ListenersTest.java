package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ListenersTest extends AbstractMemoryRunnerTest {
    @Test
    void success() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "OK")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ok"));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution-success-listener"));
    }

    @Test
    void failed() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "KO")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ko"));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution-failed-listener"));
    }
}
