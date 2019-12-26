package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SwitchTest extends AbstractMemoryRunnerTest {
    @Test
    void switchFirst() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "FIRST")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("1st"));
    }

    @Test
    void switchSecond() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "SECOND")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("2nd"));
    }

    @Test
    void switchThird() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "THIRD")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("3th"));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("failed"));
        assertThat(execution.getTaskRunList().get(3).getTaskId(), is("error-1st"));
    }

    @Test
    void switchDefault() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "DEFAULT")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("default"));
    }
}