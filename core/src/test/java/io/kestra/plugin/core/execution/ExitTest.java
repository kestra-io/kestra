package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
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
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class ExitTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    @ExecuteFlow("flows/valids/exit.yaml")
    void shouldExitTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @LoadFlows("flows/valids/exit-killed.yaml")
    void shouldExitAndKillTheExecution() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);// We need to wait for 3 execution modifications to be sure all tasks are passed to KILLED
        AtomicReference<Execution> killedExecution = new AtomicReference<>();
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("exit-killed") && execution.getState().getCurrent().isKilled()) {
                killedExecution.set(execution);
                countDownLatch.countDown();
            }
        });

        // we cannot use the runnerUtils as it may not see the RUNNING state before the execution is killed
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "exit-killed", Optional.empty()).orElseThrow();
        Execution execution = Execution.newExecution(flow, null, null, Optional.empty());
        executionQueue.emit(execution);

        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        assertThat(killedExecution.get()).isNotNull();
        assertThat(killedExecution.get().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killedExecution.get().getTaskRunList().size()).isEqualTo(2);
        assertThat(killedExecution.get().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killedExecution.get().getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);
        receive.blockLast();
    }
}