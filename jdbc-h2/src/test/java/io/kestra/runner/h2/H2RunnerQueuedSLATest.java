package io.kestra.runner.h2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest(startRunner = true)
class H2RunnerQueuedSLATest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Inject
    private LogDataStoreInterface logDataStore;

    @Test
    @LoadFlows(value = "flows/valids/sla-queued.yaml", tenantId = "sla-queued")
    void shouldNotRunTasksWhenSLAExpiresWhileQueued() throws QueueException {
        var flow = flowRepository.findById("sla-queued", "io.kestra.tests", "sla-queued", Optional.empty()).orElseThrow();
        Execution running = runnerUtils.runOneUntilRunning("sla-queued", flow.getNamespace(), flow.getId(), null, null, Duration.ofSeconds(30));
        List<Execution> queued = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Execution execution = Execution.newExecution(flow, List.of()).toBuilder()
                .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, running.getState().getStartDate().minusSeconds(1)))))
                .build();
            queued.add(runnerUtils.emitAndAwaitExecution(e -> e.getState().isQueued(), execution));
        }

        runnerUtils.awaitExecution(e -> e.getState().isTerminated(), running, Duration.ofSeconds(40));
        for (Execution execution : queued) {
            Execution terminated = runnerUtils.awaitExecution(e -> e.getState().isTerminated(), execution, Duration.ofSeconds(40));
            assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(terminated.getTaskRunList()).isNullOrEmpty();
            assertThat(terminated.getState().getHistories()).extracting(State.History::getState).doesNotContain(State.Type.RUNNING);
            assertThat(logDataStore.findByExecutionIdWithoutAcl(flow.getTenantId(), terminated.getId(), null))
                .noneMatch(log -> "SIDE EFFECT".equals(log.getMessage()));
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
            () -> assertThat(concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).orElseThrow().getRunning()).isZero()
        );
    }
}
