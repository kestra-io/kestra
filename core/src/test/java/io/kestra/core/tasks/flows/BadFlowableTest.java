package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class BadFlowableTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "bad-flowable");

        assertThat("Task runs were: \n"+ JacksonMapper.log(execution.getTaskRunList()), execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test // this test is a non-reg for an infinite loop in the executor
    void flowableWithParentFail() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "flowable-with-parent-fail");

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
