package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.kestra.core.utils.TestsUtils.stringify;

@Singleton
public class TestRunnerUtils {
    public static final Duration DEFAULT_MAX_WAIT_DURATION = Duration.ofSeconds(15);

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private ExecutionService executionService;

    public Execution runOne(String tenantId, String namespace, String flowId)
        throws TimeoutException, QueueException {
        return this.runOne(tenantId, namespace, flowId, null, null, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision)
        throws TimeoutException, QueueException {
        return this.runOne(tenantId, namespace, flowId, revision, null, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs)
        throws TimeoutException, QueueException {
        return this.runOne(tenantId, namespace, flowId, revision, inputs, null, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Duration duration)
        throws TimeoutException, QueueException {
        return this.runOne(tenantId, namespace, flowId, null, null, duration, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws TimeoutException, QueueException {
        return this.runOne(tenantId, namespace, flowId, revision, inputs, duration, null);
    }

    public Execution runOne(String tenantId, String namespace, String flowId, Integer revision, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration, List<Label> labels)
        throws TimeoutException, QueueException {
        return this.runOne(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration,
            labels);
    }

    public Execution runOne(Flow flow, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs)
        throws TimeoutException, QueueException {
        return this.runOne(flow, inputs, null, null);
    }

    public Execution runOne(Flow flow, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws TimeoutException, QueueException {
        return this.runOne(flow, inputs, duration, null);
    }

    public Execution runOne(Flow flow, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration, List<Label> labels)
        throws TimeoutException, QueueException {
        if (duration == null) {
            duration = Duration.ofSeconds(15);
        }

        Execution execution = Execution.newExecution(flow, inputs, labels, Optional.empty());

        return runOne(execution, flow, duration);
    }

    public Execution runOne(Execution execution, Flow flow, Duration duration)
        throws TimeoutException, QueueException {
        return this.emitAndAwaitExecution(isTerminatedExecution(execution, flow),  execution, duration);
    }

    public Execution runOneUntilPaused(String tenantId, String namespace, String flowId)
        throws QueueException {
        return this.runOneUntilPaused(tenantId, namespace, flowId, null, null, null);
    }

    public Execution runOneUntilPaused(String tenantId, String namespace, String flowId, Integer revision, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws QueueException {
        return this.runOneUntilPaused(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    public Execution runOneUntilPaused(Flow flow, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws QueueException {
        if (duration == null) {
            duration = DEFAULT_MAX_WAIT_DURATION;
        }

        Execution execution = Execution.newExecution(flow, inputs, null, Optional.empty());

        return this.emitAndAwaitExecution(isPausedExecution(execution), execution, duration);
    }

    public Execution runOneUntilRunning(String tenantId, String namespace, String flowId)
        throws QueueException {
        return this.runOneUntilRunning(tenantId, namespace, flowId, null, null, null);
    }

    public Execution runOneUntilRunning(String tenantId, String namespace, String flowId, Integer revision, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws QueueException {
        return this.runOneUntilRunning(
            flowRepository
                .findById(tenantId, namespace, flowId, revision != null ? Optional.of(revision) : Optional.empty())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'")),
            inputs,
            duration
        );
    }

    public Execution runOneUntilRunning(Flow flow, BiFunction<FlowInterface, Execution, Map<String, Object>> inputs, Duration duration)
        throws QueueException {
        if (duration == null) {
            duration = DEFAULT_MAX_WAIT_DURATION;
        }

        Execution execution = Execution.newExecution(flow, inputs, null, Optional.empty());

        return this.emitAndAwaitExecution(isRunningExecution(execution), execution, duration);
    }

    public Execution emitAndAwaitExecution(Predicate<Execution> predicate, Execution execution) throws QueueException {
        return emitAndAwaitExecution(predicate, execution, Duration.ofSeconds(20));
    }

    public Execution restartExecution(Predicate<Execution> predicate, Execution execution)
        throws QueueException, InterruptedException {
        return restartExecution(predicate, execution, Duration.ofSeconds(20));
    }

    public Execution restartExecution(Predicate<Execution> predicate, Execution execution, Duration duration)
        throws QueueException, InterruptedException {
        //We need to wait before restarting to make sure the execution is cleaned before we restart.
        Thread.sleep(100L);
        return emitAndAwaitExecution(seenExecution -> predicate.test(seenExecution) && seenExecution.getState().getHistories().stream().map(State.History::getState).anyMatch(State.Type.RESTARTED::equals), execution, duration);
    }

    public Execution emitAndAwaitExecution(Predicate<Execution> predicate, Execution execution, Duration duration)
        throws QueueException {

        this.executionQueue.emit(execution);

        return awaitExecution(predicate, execution, duration);
    }

    public Execution awaitExecution(Predicate<Execution> predicate, Execution execution) {
        return awaitExecution(predicate, execution, Duration.ofSeconds(20));
    }

    public Execution awaitExecution(Predicate<Execution> predicate, Execution execution, Duration duration) {
        try {

            if (duration == null){
                duration = Duration.ofSeconds(20);
            }
            return Await.until(() -> {
                Optional<Execution> exec = executionRepository.findById(execution.getTenantId(), execution.getId());
                if (exec.isPresent() && predicate.test(exec.get())) {
                    return exec.get();
                }
                return null;
            }, Duration.ofMillis(10), duration);

        } catch (TimeoutException e) {
            Optional<Execution> byId = executionRepository.findById(execution.getTenantId(), execution.getId());
            if (byId.isPresent()) {
                Execution exec = byId.get();
                throw new RuntimeException("Execution %s is currently at the status %s which is not the awaited one, full execution object:\n%s".formatted(exec.getId(), exec.getState().getCurrent(), stringify(exec)));
            } else {
                throw new RuntimeException("Execution %s doesn't exist in the database".formatted(execution.getId()));
            }
        }
    }

    /**
     * This method will return the last created execution
     * @param predicate
     * @param tenantId
     * @param namespace
     * @param flowId
     * @return
     */
    public Execution awaitFlowExecution(Predicate<Execution> predicate, String tenantId, String namespace, String flowId) {
        return awaitFlowExecution(predicate, tenantId, namespace, flowId, null);
    }

    public Execution awaitFlowExecution(Predicate<Execution> predicate, String tenantId, String namespace, String flowId, Duration duration) {
        try {

            if (duration == null){
                duration = Duration.ofSeconds(20);
            }
            return Await.until(() -> {
                ArrayListTotal<Execution> byFlowId = executionRepository.findByFlowId(
                    tenantId, namespace, flowId, Pageable.UNPAGED);
                if (!byFlowId.isEmpty()) {
                    List<Execution> matches = byFlowId.stream()
                        .filter(predicate)
                        .sorted(Comparator.comparing(e -> e.getMetadata().getOriginalCreatedDate()))
                        .toList();
                    if (!matches.isEmpty()) {
                        return matches.getLast();
                    }
                }
                return null;
            }, Duration.ofMillis(50), duration);

        } catch (TimeoutException e) {
            ArrayListTotal<Execution> byFlowId = executionRepository.findByFlowId(
                tenantId, namespace, flowId, Pageable.UNPAGED);
            if (!byFlowId.isEmpty()) {
                Execution exec = byFlowId.getLast();
                throw new RuntimeException("Execution %s is currently at the status %s which is not the awaited one".formatted(exec.getId(), exec.getState().getCurrent()));
            } else {
                throw new RuntimeException("No execution for flow %s exist in the database".formatted(flowId));
            }
        }
    }

    public List<Execution> awaitFlowExecutionNumber(int number, String tenantId, String namespace, String flowId) {
        return awaitFlowExecutionNumber(number, tenantId, namespace, flowId, null);
    }

    public List<Execution> awaitFlowExecutionNumber(int number, String tenantId, String namespace, String flowId, Duration duration) {
        AtomicReference<List<Execution>> receive = new AtomicReference<>();
        Flow flow = flowRepository
            .findById(tenantId, namespace, flowId, Optional.empty())
            .orElseThrow(
                () -> new IllegalArgumentException("Unable to find flow '" + flowId + "'"));
        try {
            if (duration == null){
                duration = Duration.ofSeconds(20);
            }
            Await.until(() -> {
                ArrayListTotal<Execution> byFlowId = executionRepository.findByFlowId(
                    tenantId, namespace, flowId, Pageable.UNPAGED);
                if (byFlowId.size() == number
                        && byFlowId.stream()
                            .filter(e -> executionService.isTerminated(flow, e))
                            .toList().size() == number) {
                    receive.set(byFlowId);
                    return true;
                }
                return false;
            }, Duration.ofMillis(50), duration);

        } catch (TimeoutException e) {
            ArrayListTotal<Execution> byFlowId = executionRepository.findByFlowId(
                tenantId, namespace, flowId, Pageable.UNPAGED);
            if (!byFlowId.isEmpty()) {
                throw new RuntimeException("%d Execution found for flow %s, but %d where awaited".formatted(byFlowId.size(), flowId, number));
            } else {
                throw new RuntimeException("No execution for flow %s exist in the database".formatted(flowId));
            }
        }

        return receive.get();
    }

    public Execution killExecution(Execution execution) throws QueueException {
        killQueue.emit(ExecutionKilledExecution.builder()
            .executionId(execution.getId())
            .isOnKillCascade(true)
            .state(ExecutionKilled.State.REQUESTED)
            .tenantId(execution.getTenantId())
            .build());

        return awaitExecution(isTerminatedExecution(
            execution,
            flowRepository
                .findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.ofNullable(execution.getFlowRevision()))
                .orElse(null)
        ), execution);
    }

    @VisibleForTesting
    public Execution awaitChildExecution(Flow flow, Execution parentExecution, Execution execution, Duration duration)
        throws QueueException {
        return this.emitAndAwaitExecution(isTerminatedChildExecution(parentExecution, flow), execution, duration);
    }

    private Predicate<Execution> isTerminatedExecution(Execution execution, Flow flow) {
        return e -> e.getId().equals(execution.getId()) && executionService.isTerminated(flow, e);
    }

    private Predicate<Execution> isPausedExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isPaused() && e.getTaskRunList() != null && e.getTaskRunList().stream().anyMatch(t -> t.getState().isPaused());
    }

    private Predicate<Execution> isRunningExecution(Execution execution) {
        return e -> e.getId().equals(execution.getId()) && e.getState().isRunning() && e.getTaskRunList() != null && e.getTaskRunList().stream().anyMatch(t -> t.getState().isRunning());
    }

    private Predicate<Execution> isTerminatedChildExecution(Execution parentExecution, Flow flow) {
        return e -> e.getParentId() != null && e.getParentId().equals(parentExecution.getId()) && executionService.isTerminated(flow, e);
    }
}
