package io.kestra.plugin.core.flow;

import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class BadFlowableTest {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @ExecuteFlow(value = "flows/valids/flowable-fail.yaml", tenantId = "sequential")
    void sequential(Execution execution) {
        assertThat(execution.getTaskRunList().size()).as("Task runs were: \n" + JacksonMapper.log(execution.getTaskRunList())).isGreaterThanOrEqualTo(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flowable-with-parent-fail.yaml", tenantId = "flowablewithparentfail")
    void flowableWithParentFail(Execution execution) throws TimeoutException {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // The parent terminates when the first sub-execution fails, but concurrent sub-executions
        // may still be running. Wait for all to reach a terminal state before asserting.
        Await.until(
            () -> executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId()).stream().allMatch(e -> e.getState().isTerminated()),
            Duration.ofMillis(100),
            Duration.ofSeconds(30)
        );
        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(2);
        assertThat(subExecutions).extracting("state.current").containsOnly(State.Type.FAILED);
    }
}
