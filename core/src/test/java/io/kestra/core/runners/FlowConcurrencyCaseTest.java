package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.History;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class FlowConcurrencyCaseTest {

    public static final String NAMESPACE = "io.kestra.tests";
    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionService executionService;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    public void flowConcurrencyCancel(String tenantId) throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-cancel", null, null, Duration.ofSeconds(30));
        try {
            List<Execution> shouldFailExecutions = List.of(
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-cancel"),
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-cancel")
            );
            assertThat(execution1.getState().isRunning()).isTrue();

            assertThat(shouldFailExecutions.stream().map(Execution::getState).map(State::getCurrent)).allMatch(Type.CANCELLED::equals);
        } finally {
            runnerUtils.killExecution(execution1);
            runnerUtils.awaitExecution(e -> e.getState().isTerminated(), execution1);
        }
    }

    public void flowConcurrencyFail(String tenantId) throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-fail", null, null, Duration.ofSeconds(30));
        try {
            List<Execution> shouldFailExecutions = List.of(
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-fail"),
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-fail")
            );

            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(shouldFailExecutions.stream().map(Execution::getState).map(State::getCurrent)).allMatch(State.Type.FAILED::equals);
        } finally {
            runnerUtils.killExecution(execution1);
            runnerUtils.awaitExecution(e -> e.getState().isTerminated(), execution1);
        }
    }

    public void flowConcurrencyQueue(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution executionResult2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(execution1.getState().isRunning()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyQueuePause(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilPaused(tenantId, NAMESPACE, "flow-concurrency-queue-pause");
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution secondExecutionResult = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution firstExecutionResult = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);


        assertThat(firstExecutionResult.getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(secondExecutionResult.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyCancelPause(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilPaused(tenantId, NAMESPACE, "flow-concurrency-cancel-pause");
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-cancel-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution secondExecutionResult = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.CANCELLED), execution2);
        Execution firstExecutionResult = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);


        assertThat(firstExecutionResult.getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(secondExecutionResult.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.getState().getHistories().get(1).getState()).isEqualTo(State.Type.CANCELLED);
    }

    public void flowConcurrencyWithForEachItem(String tenantId) throws QueueException, URISyntaxException, IOException {
        URI file = storageUpload(tenantId);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution forEachItem = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs), Duration.ofSeconds(5));
        assertThat(forEachItem.getState().getCurrent()).isEqualTo(Type.RUNNING);


        Execution terminated = runnerUtils.awaitExecution(e -> e.getState().isTerminated(), forEachItem);
        assertThat(terminated.getState().getCurrent()).isEqualTo(Type.SUCCESS);

        List<Execution> executions = runnerUtils.awaitFlowExecutionNumber(2, tenantId, NAMESPACE, "flow-concurrency-queue");

        assertThat(executions).extracting(e -> e.getState().getCurrent()).containsOnly(Type.SUCCESS);
        assertThat(executions.stream()
            .map(e -> e.getState().getHistories())
            .flatMap(List::stream)
            .map(History::getState)
            .toList()).contains(Type.QUEUED);
    }

    public void flowConcurrencyQueueRestarted(String tenantId) throws Exception {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE,
            "flow-concurrency-queue-fail", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-fail", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.RUNNING), execution2);

        // here the first fail and the second is now running.
        // we restart the first one, it should be queued then fail again.
        Execution failedExecution = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.FAILED), execution1);
        Execution restarted = executionService.restart(failedExecution, null);
        Execution executionResult1 = runnerUtils.restartExecution(
            e -> e.getState().getHistories().stream().anyMatch(history -> history.getState() == Type.RESTARTED) && e.getState().getCurrent().equals(Type.FAILED),
            restarted
        );
        Execution executionResult2 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.FAILED), execution2);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(Type.FAILED);
        // it should have been queued after restarted
        List<Type> stateList = executionResult1.getState().getHistories().stream().map(History::getState).toList();
        assertThat(stateList).contains(Type.RESTARTED);
        assertThat(stateList).contains(Type.QUEUED);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(Type.FAILED);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyQueueAfterExecution(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-after-execution", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-after-execution", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution executionResult2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencySubflow(String tenantId) throws TimeoutException, QueueException {
        runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-subflow", null, null, Duration.ofSeconds(30));
        runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-subflow");

        List<Execution> subFlowExecs = runnerUtils.awaitFlowExecutionNumber(2, tenantId, NAMESPACE, "flow-concurrency-cancel");
        assertThat(subFlowExecs).extracting(e -> e.getState().getCurrent()).containsExactlyInAnyOrder(Type.SUCCESS, Type.CANCELLED);

        // run another execution to be sure that everything works (purge is correctly done)
        Execution execution3 = runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-subflow");
        assertThat(execution3.getState().getCurrent()).isEqualTo(Type.SUCCESS);
        runnerUtils.awaitFlowExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE, "flow-concurrency-cancel");
    }

    public void flowConcurrencyParallelSubflowKill(String tenantId) throws QueueException {
        Execution parent = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-parallel-subflow-kill", null, null, Duration.ofSeconds(30));
        Execution queued = runnerUtils.awaitFlowExecution(e -> e.getState().isQueued(), tenantId, NAMESPACE, "flow-concurrency-parallel-subflow-kill-child");

        // Kill the parent
        killQueue.emit(ExecutionKilledExecution
            .builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionId(parent.getId())
            .isOnKillCascade(true)
            .tenantId(tenantId)
            .build()
        );

        Execution terminated = runnerUtils.awaitExecution(e -> e.getState().isTerminated(), queued);
        assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(terminated.getState().getHistories().stream().noneMatch(h -> h.getState() == Type.RUNNING)).isTrue();
        assertThat(terminated.getTaskRunList()).isNull();
    }

    public void flowConcurrencyKilled(String tenantId) throws QueueException, InterruptedException {
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-killed", Optional.empty())
            .orElseThrow();
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-killed", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));
        Execution execution3 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));

        try {
            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(execution2.getState().getCurrent()).isEqualTo(Type.QUEUED);
            assertThat(execution3.getState().getCurrent()).isEqualTo(Type.QUEUED);

            // we kill execution 1, execution 2 should run but not execution 3
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .state(ExecutionKilled.State.REQUESTED)
                .executionId(execution1.getId())
                .isOnKillCascade(true)
                .tenantId(tenantId)
                .build()
            );

            Execution killed = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.KILLED), execution1);
            assertThat(killed.getState().getCurrent()).isEqualTo(Type.KILLED);
            assertThat(killed.getState().getHistories().stream().anyMatch(h -> h.getState() == Type.RUNNING)).isTrue();

            // we now check that execution 2 is running
            Execution running = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.RUNNING), execution2);
            assertThat(running.getState().getCurrent()).isEqualTo(Type.RUNNING);

            // we check that execution 3 is still queued
            Thread.sleep(100); // wait a little to be 100% sure
            Execution queued = runnerUtils.awaitExecution(e -> e.getState().isQueued(), execution3);
            assertThat(queued.getState().getCurrent()).isEqualTo(Type.QUEUED);
        } finally {
            // kill everything to avoid dangling executions
            runnerUtils.killExecution(execution2);
            runnerUtils.killExecution(execution3);

            // await that they are all terminated, note that as KILLED is received twice, some messages would still be pending, but this is the best we can do
            runnerUtils.awaitFlowExecutionNumber(3, tenantId, NAMESPACE, "flow-concurrency-queue-killed");
        }
    }

    public void flowConcurrencyQueueKilled(String tenantId) throws QueueException, InterruptedException {
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-killed", Optional.empty())
            .orElseThrow();
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-killed", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));
        Execution execution3 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));

        try {
            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(execution2.getState().getCurrent()).isEqualTo(Type.QUEUED);
            assertThat(execution3.getState().getCurrent()).isEqualTo(Type.QUEUED);

            // we kill execution 2, execution 3 should not run
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .state(ExecutionKilled.State.REQUESTED)
                .executionId(execution2.getId())
                .isOnKillCascade(true)
                .tenantId(tenantId)
                .build()
            );

            Execution killed = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.KILLED), execution2);
            assertThat(killed.getState().getCurrent()).isEqualTo(Type.KILLED);
            assertThat(killed.getState().getHistories().stream().noneMatch(h -> h.getState() == Type.RUNNING)).isTrue();

            // we now check that execution 3 is still queued
            Thread.sleep(100); // wait a little to be 100% sure
            Execution queued = runnerUtils.awaitExecution(e -> e.getState().isQueued(), execution3);
            assertThat(queued.getState().getCurrent()).isEqualTo(Type.QUEUED);
        } finally {
            // kill everything to avoid dangling executions
            runnerUtils.killExecution(execution1);
            runnerUtils.killExecution(execution3);

            // await that they are all terminated, note that as KILLED is received twice, some messages would still be pending, but this is the best we can do
            runnerUtils.awaitFlowExecutionNumber(3, tenantId, NAMESPACE, "flow-concurrency-queue-killed");
        }
    }

    private URI storageUpload(String tenantId) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
            tenantId,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content() {
        return IntStream
            .range(0, 7)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }

}
