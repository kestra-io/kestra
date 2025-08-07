package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class KillTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    @LoadFlows("flows/valids/kill.yaml")
    void shouldKillTheExecution() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> killedExecution = new AtomicReference<>();

        // Listen for the final KILLED state on the execution queue
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("kill") && execution.getState().getCurrent().isKilled()) {
                killedExecution.set(execution);
                countDownLatch.countDown();
            }
        });

        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "kill", Optional.empty()).orElseThrow();
        Execution execution = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution);

        // Wait for the KILLED state to be received
        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receive.blockLast(); // Stop listening

        Execution finalExecution = killedExecution.get();
        assertThat(finalExecution).isNotNull();
        assertThat(finalExecution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(finalExecution.getTaskRunList()).hasSize(2);
        assertThat(finalExecution.getTaskRunList().get(0).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(finalExecution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @LoadFlows({"flows/valids/kill-with-propagation.yaml", "flows/valids/child-flow.yaml"})
    void shouldKillTheExecutionWithPropagation() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2); // We expect two KILLED executions (parent and child)
        AtomicReference<Execution> parentExecution = new AtomicReference<>();
        AtomicReference<Execution> childExecution = new AtomicReference<>();
        AtomicBoolean parentKilled = new AtomicBoolean(false);
        AtomicBoolean childKilled = new AtomicBoolean(false);

        // Listen for the final KILLED state on the execution queue for both flows
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getState().getCurrent() == State.Type.KILLED) {
                if (execution.getFlowId().equals("kill-with-propagation") && !parentKilled.get()) {
                    parentExecution.set(execution);
                    parentKilled.set(true);
                    countDownLatch.countDown();
                } else if (execution.getFlowId().equals("child-flow") && !childKilled.get()) {
                    childExecution.set(execution);
                    childKilled.set(true);
                    countDownLatch.countDown();
                }
            }
        });

        // Manually trigger the parent flow
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "kill-with-propagation", Optional.empty()).orElseThrow();
        Execution execution = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution);

        // Wait for both KILLED states to be received
        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receive.blockLast(); // Stop listening

        // Assertions for parent flow
        Execution finalParentExecution = parentExecution.get();
        assertThat(finalParentExecution).isNotNull();
        assertThat(finalParentExecution.getState().getCurrent()).isEqualTo(State.Type.KILLED);

        // Assertions for child flow
        Execution finalChildExecution = childExecution.get();
        assertThat(finalChildExecution).isNotNull();
        assertThat(finalChildExecution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @ExecuteFlow("flows/valids/kill-conditional.yaml")
    void shouldKillExecutionConditionally(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @ExecuteFlow("flows/valids/kill-without-propagation.yaml")
    void shouldKillExecutionWithoutPropagation(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }
}
