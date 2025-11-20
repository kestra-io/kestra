package io.kestra.executor.handler;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.executor.*;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class ExecutionEventMessageHandler implements ExecutorMessageHandler<ExecutionEvent> {
    @Inject
    private ExecutionStateStore executionStateStore;
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;
    @Inject
    private ExecutionDelayStateStore executionDelayStateStore;
    @Inject
    private SLAMonitorStateStore  slaMonitorStateStore;
    @Inject
    private ConcurrencyLimitStateStore concurrencyLimitStateStore;

    @Inject
    private ExecutorService executorService;
    @Inject
    private WorkerGroupService workerGroupService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private QueueInterface<WorkerJob> workerJobQueue;
    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    private final Tracer tracer;

    @Inject
    public ExecutionEventMessageHandler(TracerFactory tracerFactory) {
        this.tracer = tracerFactory.getTracer(DefaultExecutor.class, "EXECUTOR");
    }

    @Override
    public Optional<ExecutorContext> handle(ExecutionEvent message) {
        return executionStateStore.lock(message.executionId(), execution -> tracer.inCurrentContext(
            execution,
            FlowId.uidWithoutRevision(execution),
            () -> {
                try {
                    final FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow();
                    ExecutorContext executor = new ExecutorContext(execution, flow);

                    // schedule it for later if needed
                    if (execution.getState().getCurrent() == State.Type.CREATED && execution.getScheduleDate() != null && execution.getScheduleDate().isAfter(Instant.now())) {
                        ExecutionDelay executionDelay = ExecutionDelay.builder()
                            .executionId(executor.getExecution().getId())
                            .date(execution.getScheduleDate())
                            .state(State.Type.RUNNING)
                            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                            .build();
                        executionDelayStateStore.save(executionDelay);
                        return executor;
                    }

                    // create an SLA monitor if needed
                    if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted()) && !ListUtils.isEmpty(flow.getSla())) {
                        List<SLAMonitor> monitors = flow.getSla().stream()
                            .filter(ExecutionMonitoringSLA.class::isInstance)
                            .map(ExecutionMonitoringSLA.class::cast)
                            .map(sla -> SLAMonitor.builder()
                                .executionId(execution.getId())
                                .slaId(((SLA) sla).getId())
                                .deadline(execution.getState().getStartDate().plus(sla.getDuration()))
                                .build()
                            )
                            .toList();
                        monitors.forEach(monitor -> slaMonitorStateStore.save(monitor));
                    }

                    // handle concurrency limit, we need to use a different queue to be sure that execution running
                    // are processed sequentially so inside a queue with no parallelism
                    if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted()) && flow.getConcurrency() != null) {
                        ExecutionRunning executionRunning = ExecutionRunning.builder()
                            .tenantId(executor.getFlow().getTenantId())
                            .namespace(executor.getFlow().getNamespace())
                            .flowId(executor.getFlow().getId())
                            .execution(executor.getExecution())
                            .concurrencyState(ExecutionRunning.ConcurrencyState.CREATED)
                            .build();

                        ExecutionRunning processed = concurrencyLimitStateStore.countThenProcess(flow, (txContext, concurrencyLimit) -> {
                            ExecutionRunning computed = executorService.processExecutionRunning(flow, concurrencyLimit.getRunning(), executionRunning.withExecution(execution)); // be sure that the execution running contains the latest value of the execution
                            if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.RUNNING && !computed.getExecution().getState().isTerminated()) {
                                return Pair.of(computed, concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1));
                            } else if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                executionQueuedStateStore.save(txContext, ExecutionQueued.fromExecutionRunning(computed));
                            }
                            return Pair.of(computed, concurrencyLimit);
                        });

                        // if the execution is queued or terminated due to concurrency limit, we stop here
                        if (processed.getExecution().getState().isTerminated() || processed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                            return executor.withExecution(processed.getExecution(), "handleConcurrencyLimit");
                        }
                    }

                    // handle execution changed SLA
                    executor = executorService.handleExecutionChangedSLA(executor);

                    // process the execution
                    if (log.isDebugEnabled()) {
                        executorService.log(log, true, executor);
                    }
                    executor = executorService.process(executor);

                    if (!executor.getNexts().isEmpty()) {
                        executor.withExecution(
                            executorService.onNexts(executor.getExecution(), executor.getNexts()),
                            "onNexts"
                        );
                    }

                    // worker task
                    if (!executor.getWorkerTasks().isEmpty()) {
                        List<WorkerTaskResult> workerTaskResults = new ArrayList<>();
                        executor
                            .getWorkerTasks()
                            .forEach(throwConsumer(workerTask -> {
                                try {
                                    if (!TruthUtils.isTruthy(workerTask.getRunContext().render(workerTask.getTask().getRunIf()))) {
                                        workerTaskResults.add(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.SKIPPED)));
                                    } else {
                                        if (workerTask.getTask().isSendToWorkerTask()) {
                                            Optional<WorkerGroup> maybeWorkerGroup = workerGroupService.resolveGroupFromJob(flow, workerTask);
                                            String workerGroupKey = maybeWorkerGroup.map(throwFunction(workerGroup -> workerTask.getRunContext().render(workerGroup.getKey())))
                                                .orElse(null);
                                            if (workerTask.getTask() instanceof WorkingDirectory) {
                                                // WorkingDirectory is a flowable so it will be moved to RUNNING a few lines under
                                                workerJobQueue.emit(workerGroupKey, workerTask);
                                            } else {
                                                TaskRun taskRun = workerTask.getTaskRun().withState(State.Type.SUBMITTED);
                                                workerJobQueue.emit(workerGroupKey, workerTask.withTaskRun(taskRun));
                                                workerTaskResults.add(new WorkerTaskResult(taskRun));
                                            }
                                        }
                                        /// flowable attempt state transition to running
                                        if (workerTask.getTask().isFlowable()) {
                                            List<TaskRunAttempt> attempts = Optional.ofNullable(workerTask.getTaskRun().getAttempts())
                                                .map(ArrayList::new)
                                                .orElseGet(ArrayList::new);


                                            attempts.add(
                                                TaskRunAttempt.builder()
                                                    .state(new State().withState(State.Type.RUNNING))
                                                    .build()
                                            );

                                            TaskRun updatedTaskRun = workerTask.getTaskRun()
                                                .withAttempts(attempts)
                                                .withState(State.Type.RUNNING);

                                            workerTaskResults.add(new WorkerTaskResult(updatedTaskRun));
                                        }
                                    }
                                } catch (Exception e) {
                                    workerTaskResults.add(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.FAILED)));
                                    workerTask.getRunContext().logger().error("Failed to evaluate the runIf condition for task {}. Cause: {}", workerTask.getTask().getId(), e.getMessage(), e);
                                }
                            }));

                        try {
                            executorService.addWorkerTaskResults(executor, workerTaskResults);
                        } catch (InternalException e) {
                            log.error("Unable to add a worker task result to the execution", e);
                        }
                    }

                    // subflow execution results
                    if (!executor.getSubflowExecutionResults().isEmpty()) {
                        executor.getSubflowExecutionResults()
                            .forEach(throwConsumer(subflowExecutionResult -> subflowExecutionResultQueue.emit(subflowExecutionResult)));
                    }

                    // schedulerDelay
                    if (!executor.getExecutionDelays().isEmpty()) {
                        executor.getExecutionDelays()
                            .forEach(executionDelay -> executionDelayStateStore.save(executionDelay));
                    }

                    // subflow executions
                    if (!executor.getSubflowExecutions().isEmpty()) {
                        executor.getSubflowExecutions().forEach(throwConsumer(subflowExecution -> {
                            Execution subExecution = subflowExecution.getExecution();
                            String msg = String.format("Created new execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]]", subExecution.getId(), subExecution.getFlowId(), subExecution.getNamespace());

                            log.info(msg);

                            logQueue.emit(LogEntry.of(subflowExecution.getParentTaskRun(), subflowExecution.getExecution().getKind()).toBuilder()
                                .level(Level.INFO)
                                .message(msg)
                                .timestamp(subflowExecution.getParentTaskRun().getState().getStartDate())
                                .thread(Thread.currentThread().getName())
                                .build()
                            );

                            executionQueue.emit(subflowExecution.getExecution());
                        }));
                    }

                    return executor;
                } catch (QueueException e) {
                    try {
                        Execution failedExecution = fail(execution, e);
                        this.executionQueue.emit(failedExecution);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", execution.getId(), ex);
                    }
                    Span.current().recordException(e).setStatus(StatusCode.ERROR);

                    return null;
                }
            }
        ));
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        try {
            logQueue.emitAsync(failedExecution.getLogs());
        } catch (QueueException ex) {
            // fail silently
        }
        return failedExecution.getExecution().getState().isFailed() ? failedExecution.getExecution() :  failedExecution.getExecution().withState(State.Type.FAILED);
    }
}
