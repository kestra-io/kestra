package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Processes matured {@link ExecutionDelay}s — paused flows to resume, failed tasks/flows to retry,
 * WaitFor tasks to continue — and returns the resulting {@link ExecutorContext}s to the caller.
 * <p>
 * IMPORTANT — transactional outbox: {@link #processExpired(Instant)} runs its callback inside the
 * delay state store's transaction ({@code processExpired} implementations wrap the whole iteration
 * in a single transaction on JDBC). Queue messages must never be emitted from inside that
 * transaction: on brokers with their own transactionality (e.g. Kafka), a consumer can observe the
 * message before the state-store transaction commits, find no execution row, and silently drop the
 * event. This class therefore deliberately has <b>no queue dependency</b> — it only collects and
 * returns the contexts, and the caller emits them <b>after</b> this method returns, i.e. after the
 * transaction has committed.
 */
@Singleton
public class ExecutionDelayProcessor {
    private final ExecutionDelayStateStore executionDelayStateStore;
    private final ExecutionStateStore executionStateStore;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ExecutionService executionService;
    private final ExecutorService executorService;
    private final MetricRegistry metricRegistry;

    @Inject
    public ExecutionDelayProcessor(
        ExecutionDelayStateStore executionDelayStateStore,
        ExecutionStateStore executionStateStore,
        FlowMetaStoreInterface flowMetaStore,
        ExecutionService executionService,
        ExecutorService executorService,
        MetricRegistry metricRegistry) {
        this.executionDelayStateStore = executionDelayStateStore;
        this.executionStateStore = executionStateStore;
        this.flowMetaStore = flowMetaStore;
        this.executionService = executionService;
        this.executorService = executorService;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Processes every delay matured at {@code now} and returns the updated executor contexts.
     * The caller must emit the returned contexts only after this method returns — never from
     * inside the state-store transaction (see the class Javadoc).
     */
    public List<ExecutorContext> processExpired(Instant now) {
        List<ExecutorContext> executors = new ArrayList<>();

        executionDelayStateStore.processExpired(
            now, executionDelay -> process(executionDelay).ifPresent(executors::add)
        );

        return executors;
    }

    private Optional<ExecutorContext> process(ExecutionDelay executionDelay) {
        return executionStateStore.lock(executionDelay.getExecutionId(), execution ->
        {
            ExecutorContext executor = new ExecutorContext(execution);

            metricRegistry
                .counter(
                    MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT_DESCRIPTION,
                    metricRegistry.tags(executor.getExecution())
                )
                .increment();

            try {
                // Handle paused tasks and scheduledAt
                // Also skip if the execution is being killed (KILLING is not yet terminated but must not be resumed).
                if (
                    executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESUME_FLOW)
                        && !execution.getState().isTerminated()
                        && execution.getState().getCurrent() != State.Type.KILLING
                ) {
                    if (executionDelay.getTaskRunId() == null) {
                        // if taskRunId is null, this means we restart a flow that was delayed at startup (scheduled on)
                        Execution markAsExecution = execution.withState(executionDelay.getState());
                        executor = executor.withExecution(markAsExecution, "pausedRestart");
                    } else {
                        // if there is a taskRun it means we restart a paused task
                        FlowInterface flow = flowMetaStore.findByExecution(execution).orElseThrow();
                        Execution markAsExecution = executionService.markAs(
                            execution,
                            flow,
                            executionDelay.getTaskRunId(),
                            executionDelay.getState()
                        );

                        executor = executor.withExecution(markAsExecution, "pausedRestart");
                    }
                }
                // Handle failed task retries — skip if the execution is being killed so the retry does not race the kill
                else if (
                    executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_TASK)
                        && execution.getState().getCurrent() != State.Type.KILLING
                ) {
                    FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                    Execution newAttempt = executionService.retryTask(
                        execution,
                        flow,
                        executionDelay.getTaskRunId()
                    );
                    executor = executor.withExecution(newAttempt, "retryFailedTask");
                }
                // Handle failed flow retries — skip if the execution is being killed so the retry does not race the kill
                else if (
                    executionDelay.getDelayType().equals(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)
                        && execution.getState().getCurrent() != State.Type.KILLING
                ) {
                    FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                    Execution newExecution = executionService.replay(executor.getExecution(), flow, null, null, Optional.empty());
                    executor = executor.withExecution(newExecution, "retryFailedFlow");
                }
                // Handle WaitFor
                else if (executionDelay.getDelayType().equals(ExecutionDelay.DelayType.CONTINUE_FLOWABLE)) {
                    Execution newExecution = executionService.retryWaitFor(executor.getExecution(), executionDelay.getTaskRunId());
                    executor = executor.withExecution(newExecution, "continueLoop");
                }
            } catch (Exception e) {
                executor = executorService.handleFailedExecutionFromExecutor(executor, e);
            }

            return executor;
        });
    }
}
