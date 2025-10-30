package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
@KestraTest(startRunner = true)
class ListenersTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    void initListeners() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-flowable.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-multiple.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-multiple-failed.yaml")));
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/listeners-failed.yaml")));
    }

    @Test
    void success() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "OK")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("ok");
        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat((String) execution.getTaskRunList().get(2).getOutputs().get("value")).contains("flowId=listeners");
    }

    @Test
    void failed() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "KO")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("ko");
        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("execution-failed-listener");
    }

    @Test
    void flowableExecution() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners-flowable",
            null,
            (f, e) -> ImmutableMap.of("string", "execution")
        );

        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("parent-seq");
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("execution-success-listener");
        assertThat((String) execution.getTaskRunList().get(2).getOutputs().get("value")).contains(execution.getId());
    }

    @Test
    void multipleListeners() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners-multiple"
        );

        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("l1");
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("l2");
    }

    @Test
    void failedListeners() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners-failed"
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("ko");
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void failedMultipleListeners() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "listeners-multiple-failed"
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("ko");
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("l2");
        assertThat(execution.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
