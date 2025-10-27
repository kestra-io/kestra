package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.LoadFlows;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class CorrelationIdTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_EVENT_NAMED)
    private QueueInterface<ExecutionEvent> executionEventQueue;
    @Inject
    private TestRunnerUtils runnerUtils;
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows({"flows/valids/subflow-parent.yaml",
        "flows/valids/subflow-child.yaml",
        "flows/valids/subflow-grand-child.yaml"})
    void shouldHaveCorrelationId() throws QueueException, TimeoutException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicReference<Execution> child = new AtomicReference<>();
        AtomicReference<Execution> grandChild = new AtomicReference<>();

        Flux<ExecutionEvent> receive = TestsUtils.receive(executionEventQueue, either -> {
            ExecutionEvent execution = either.getLeft();
            if (execution.flowId().equals("subflow-child") && execution.eventType() == ExecutionEventType.TERMINATED) {
                child.set(executionRepository.findById(execution.tenantId(), execution.executionId()).orElseThrow());
                countDownLatch.countDown();
            }
            if (execution.flowId().equals("subflow-grand-child") && execution.eventType() == ExecutionEventType.TERMINATED) {
                grandChild.set(executionRepository.findById(execution.tenantId(), execution.executionId()).orElseThrow());
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "subflow-parent");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receive.blockLast();

        assertThat(child.get()).isNotNull();
        assertThat(child.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Optional<Label> correlationId = child.get().getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());

        assertThat(grandChild.get()).isNotNull();
        assertThat(grandChild.get().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        correlationId = grandChild.get().getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());
    }
}
