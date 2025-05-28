package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class RestartCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionService executionService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    public void restartFailedThenSuccess() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_last_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(3);
        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getState().getCurrent() == State.Type.SUCCESS && execution.getId().equals(firstExecution.getId()),
            throwRunnable(() -> {
                Execution restartedExec = executionService.restart(firstExecution, null);
                assertThat(restartedExec).isNotNull();
                assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
                assertThat(restartedExec.getParentId()).isNull();
                assertThat(restartedExec.getTaskRunList().size()).isEqualTo(3);
                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

                executionQueue.emit(restartedExec);
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(4);

        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts().size()).isEqualTo(2);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    public void restartFailedThenFailureWithGlobalErrors() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_always_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(2);
        assertThat(firstExecution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getState().getCurrent() == State.Type.FAILED && execution.getTaskRunList().getFirst().getAttempts().size() == 2,
            throwRunnable(() -> {
                Execution restartedExec = executionService.restart(firstExecution, null);
                executionQueue.emit(restartedExec);

                assertThat(restartedExec).isNotNull();
                assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
                assertThat(restartedExec.getParentId()).isNull();
                assertThat(restartedExec.getTaskRunList().size()).isEqualTo(1);
                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void restartFailedThenFailureWithLocalErrors() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_local_errors").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(5);
        assertThat(firstExecution.getTaskRunList().get(3).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getState().getCurrent() == State.Type.FAILED && execution.findTaskRunsByTaskId("failStep").stream().findFirst().get().getAttempts().size() == 2,
            throwRunnable(() -> {
                Execution restartedExec = executionService.restart(firstExecution, null);
                executionQueue.emit(restartedExec);

                assertThat(restartedExec).isNotNull();
                assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
                assertThat(restartedExec.getParentId()).isNull();
                assertThat(restartedExec.getTaskRunList().size()).isEqualTo(4);
                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(5);

        Optional<TaskRun> taskRun = finishedRestartedExecution.findTaskRunsByTaskId("failStep").stream().findFirst();
        assertTrue(taskRun.isPresent());
        assertThat(taskRun.get().getAttempts().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void replay() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart-each").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitChildExecution(
            flow,
            firstExecution,
            throwRunnable(() -> {
                Execution restartedExec = executionService.replay(firstExecution, firstExecution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null);
                executionQueue.emit(restartedExec);

                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
                assertThat(restartedExec.getState().getHistories()).hasSize(4);
                assertThat(restartedExec.getTaskRunList()).hasSize(20);
                assertThat(restartedExec.getTaskRunList().get(19).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

                assertThat(restartedExec.getId()).isNotEqualTo(firstExecution.getId());
                assertThat(restartedExec.getTaskRunList().get(1).getId()).isNotEqualTo(firstExecution.getTaskRunList().get(1).getId());
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void restartMultiple() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "failed-first");
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.restart(execution, null);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

        Execution restartEnded = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED,
            throwRunnable(() -> executionQueue.emit(restart)),
            Duration.ofSeconds(120)
        );

        assertThat(restartEnded.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution newRestart = executionService.restart(restartEnded, null);

        restartEnded = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED,
            throwRunnable(() -> executionQueue.emit(newRestart)),
            Duration.ofSeconds(120)
        );

        assertThat(restartEnded.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void restartSubflow() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux<Execution> receiveSubflows = TestsUtils.receive(executionQueue, either -> {
            Execution subflowExecution = either.getLeft();
            if (subflowExecution.getFlowId().equals("restart-child") && subflowExecution.getState().getCurrent().isFailed()) {
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart-parent");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // here we must have 1 failed subflows
        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receiveSubflows.blockLast();

        // there is 3 values so we must restart it 3 times to end the 3 subflows
        CountDownLatch successLatch = new CountDownLatch(3);
        receiveSubflows = TestsUtils.receive(executionQueue, either -> {
            Execution subflowExecution = either.getLeft();
            if (subflowExecution.getFlowId().equals("restart-child") && subflowExecution.getState().getCurrent().isSuccess()) {
                successLatch.countDown();
            }
        });
        Execution restarted1 = executionService.restart(execution, null);
        execution = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED && e.getFlowId().equals("restart-parent"),
            throwRunnable(() -> executionQueue.emit(restarted1)),
            Duration.ofSeconds(10)
        );
        Execution restarted2 = executionService.restart(execution, null);
        execution = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED && e.getFlowId().equals("restart-parent"),
            throwRunnable(() -> executionQueue.emit(restarted2)),
            Duration.ofSeconds(10)
        );
        Execution restarted3 = executionService.restart(execution, null);
        execution = runnerUtils.awaitExecution(
            e -> e.getState().getCurrent() == State.Type.SUCCESS && e.getFlowId().equals("restart-parent"),
            throwRunnable(() -> executionQueue.emit(restarted3)),
            Duration.ofSeconds(10)
        );
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertTrue(successLatch.await(1, TimeUnit.MINUTES));
        receiveSubflows.blockLast();
    }
}
