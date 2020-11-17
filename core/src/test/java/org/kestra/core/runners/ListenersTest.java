package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class ListenersTest extends AbstractMemoryRunnerTest {
    @BeforeEach
    private void initListeners() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-flowable.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-multiple.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-multiple-failed.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-failed.yaml")));
    }

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
        assertThat((String) execution.getTaskRunList().get(2).getOutputs().get("value"), containsString("flowId=listeners"));
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

    @Test
    void flowableFlow() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners-flowable",
            null,
            (f, e) -> ImmutableMap.of("string", "flow")
        );

        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("parent-seq"));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("flow"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("1"));
    }

    @Test
    void flowableExecution() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners-flowable",
            null,
            (f, e) -> ImmutableMap.of("string", "execution")
        );

        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("parent-seq"));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void multipleListeners() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners-multiple"
        );

        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("l1"));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("l2"));
    }

    @Test
    void failedListeners() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners-failed"
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(2));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ko"));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void failedMultipleListeners() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners-multiple-failed"
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ko"));
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("l2"));
        assertThat(execution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
