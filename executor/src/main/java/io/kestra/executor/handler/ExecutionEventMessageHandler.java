package io.kestra.executor.handler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.core.services.QuotaService;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.executor.*;
import io.kestra.executor.KillSwitchActionService;
import io.kestra.plugin.core.flow.WorkingDirectory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
public class ExecutionEventMessageHandler implements ExecutorMessageHandler<ExecutionEvent> {
    private final ExecutionStateStore executionStateStore;
    private final ExecutionQueuedStateStore executionQueuedStateStore;
    private final ExecutionDelayStateStore executionDelayStateStore;
    private final SLAMonitorStateStore slaMonitorStateStore;
    private final ConcurrencyLimitStateStore concurrencyLimitStateStore;
    private final ConcurrencyLimitResolver concurrencyLimitResolver;
    private final ExecutorService executorService;
    private final WorkerQueueService workerGroupService;
    private final QuotaService quotaService;
    private final FlowMetaStoreInterface flowMetaStore;
    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    private final DispatchQueueInterface<Execution> executionQueue;
    private final RunContextLoggerFactory runContextLoggerFactory;
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final MetricRegistry metricRegistry;
    private final Tracer tracer;

    @Inject
    public ExecutionEventMessageHandler(
        ExecutionStateStore executionStateStore,
        ExecutionQueuedStateStore executionQueuedStateStore,
        ExecutionDelayStateStore executionDelayStateStore,
        SLAMonitorStateStore slaMonitorStateStore,
        ConcurrencyLimitStateStore concurrencyLimitStateStore,
        ConcurrencyLimitResolver concurrencyLimitResolver,
        ExecutorService executorService,
        WorkerQueueService workerGroupService,
        QuotaService quotaService,
        FlowMetaStoreInterface flowMetaStore,
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue,
        DispatchQueueInterface<Execution> executionQueue,
        RunContextLoggerFactory runContextLoggerFactory,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        MetricRegistry metricRegistry,
        TracerFactory tracerFactory) {
        this.executionStateStore = executionStateStore;
        this.executionQueuedStateStore = executionQueuedStateStore;
        this.executionDelayStateStore = executionDelayStateStore;
        this.slaMonitorStateStore = slaMonitorStateStore;
        this.concurrencyLimitStateStore = concurrencyLimitStateStore;
        this.concurrencyLimitResolver = concurrencyLimitResolver;
        this.executorService = executorService;
        this.workerGroupService = workerGroupService;
        this.quotaService = quotaService;
        this.flowMetaStore = flowMetaStore;
        this.workerJobEventQueue = workerJobEventQueue;
        this.subflowExecutionResultQueue = subflowExecutionResultQueue;
        this.executionQueue = executionQueue;
        this.runContextLoggerFactory = runContextLoggerFactory;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
        this.metricRegistry = metricRegistry;
        this.tracer = tracerFactory.getTracer(DefaultExecutor.class, "EXECUTOR");
    }

    @Override
    public Optional<ExecutorContext> handle(ExecutionEvent message) {
        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType != EvaluationType.PASS) {
            var execution = executionStateStore.findById(message.executionId());
            if (execution != null && evaluationType.isKillSwitched(execution)) {
                killSwitchActionService.handle(evaluationType, execution.getTenantId(), execution.getId());
                return Optional.empty();
            }
        }

        return executionStateStore.lock(
            message.executionId(), execution -> tracer.inCurrentContext(
                execution,
                FlowId.uidWithoutRevision(execution),
                () ->
                {
                    try {
                        final FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));

                        // A flow that resolves to a FlowWithException (unparsable, or blocked at execution
                        // pre-flight) cannot be processed: fail the execution fast instead of leaving it stuck.
                        // Terminated executions are left untouched so a late flow change never rewrites them.
                        if (flow instanceof FlowWithException flowWithException) {
                            if (execution.getState().isTerminated()) {
                                return null;
                            }
                            IllegalStateException exception = new IllegalStateException(flowWithException.getException());
                            Span.current().recordException(exception).setStatus(StatusCode.ERROR);
                            Execution failedExecution = fail(execution, exception);
                            return new ExecutorContext(execution).withExecution(failedExecution, "flowWithException");
                        }

                        ExecutorContext executor = new ExecutorContext(execution, flow);

                        // schedule it for later if needed
                        if (execution.getState().getCurrent() == State.Type.CREATED && execution.getScheduleDate() != null && execution.getScheduleDate().isAfter(Instant.now())) {
                            ExecutionDelay executionDelay = ExecutionDelay.builder()
                                .executionId(executor.getExecution().getId())
                                .date(execution.getScheduleDate())
                                .state(State.Type.CREATED)
                                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                                .build();
                            executionDelayStateStore.save(executionDelay);
                            return executor;
                        }

                        // process actions that must be done after the execution has been created
                        if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted())) {
                            // create an SLA monitor if needed
                            if (!ListUtils.isEmpty(flow.getSla())) {
                                List<SLAMonitor> monitors = flow.getSla().stream()
                                    .filter(ExecutionMonitoringSLA.class::isInstance)
                                    .map(ExecutionMonitoringSLA.class::cast)
                                    .map(
                                        sla -> SLAMonitor.builder()
                                            .executionId(execution.getId())
                                            .slaId(((SLA) sla).getId())
                                            .deadline(execution.getState().getStartDate().plus(sla.getDuration()))
                                            .build()
                                    )
                                    .toList();
                                monitors.forEach(monitor -> slaMonitorStateStore.save(monitor));
                            }

                            // handle quotas
                            Optional<Quota> quota = quotaService.checkAndIncrement(flow);
                            if (quota.isPresent()) {
                                // a quota is exceeded: stop the execution in the desired state
                                Execution newExecution = switch (quota.get().getBehavior()) {
                                    case FAIL -> {
                                        var failedExecution = execution
                                            .failedExecutionFromExecutor(new IllegalStateException("Execution is FAILED due to " + quota.get().getDuration() + " quota limit exceeded"));
                                        var logger = runContextLoggerFactory.create(execution);
                                        logger.emitLogs(failedExecution.logs());
                                        yield failedExecution.execution();
                                    }
                                    case CANCEL -> execution.withState(State.Type.CANCELLED);
                                };

                                metricRegistry
                                    .counter(
                                        MetricRegistry.METRIC_EXECUTOR_QUOTA_EXCEEDED_COUNT, MetricRegistry.METRIC_EXECUTOR_QUOTA_EXCEEDED_COUNT_DESCRIPTION, metricRegistry.tags(execution)
                                    )
                                    .increment();

                                return executor.withExecution(newExecution, "processQuotas");
                            }

                            // handle concurrency limits — flow, namespace and tenant scoped; an execution that
                            // runs claims one slot in every scope, the first limit reached defines the behavior
                            List<ScopedConcurrencyLimit> concurrencyLimits = concurrencyLimitResolver.resolveLimits(flow);
                            if (!concurrencyLimits.isEmpty()) {
                                ExecutionRunning executionRunning = ExecutionRunning.builder()
                                    .tenantId(executor.getFlow().getTenantId())
                                    .namespace(executor.getFlow().getNamespace())
                                    .flowId(executor.getFlow().getId())
                                    .execution(executor.getExecution())
                                    .concurrencyState(ExecutionRunning.ConcurrencyState.CREATED)
                                    .build();

                                ExecutionRunning processed = concurrencyLimitStateStore.countThenProcess(flow, concurrencyLimits, (txContext, runningCounts) ->
                                {
                                    ExecutionRunning computed = executorService.processExecutionRunning(concurrencyLimits, runningCounts, executionRunning.withExecution(execution)); // be sure that the execution running contains the latest value of the execution
                                    if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.RUNNING && !computed.getExecution().getState().isTerminated()) {
                                        return Pair.of(computed, true);
                                    } else if (computed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                        executionQueuedStateStore.save(txContext, ExecutionQueued.fromExecutionRunning(computed));
                                    }
                                    return Pair.of(computed, false);
                                });

                                // if the execution is queued or terminated due to concurrency limit, we stop here
                                if (processed.getExecution().getState().isTerminated() || processed.getConcurrencyState() == ExecutionRunning.ConcurrencyState.QUEUED) {
                                    if (processed.getExecution().getState().getCurrent().isTerminatedInError()) {
                                        Span.current().setStatus(StatusCode.ERROR, "Execution ended in state " + processed.getExecution().getState().getCurrent().name());
                                    }
                                    return executor.withExecution(processed.getExecution(), "handleConcurrencyLimit");
                                }

                                // the execution claimed one slot in every scope: remember them so the release
                                // decrements exactly these, even if the definitions change while it runs
                                if (execution.getMetadata() != null) {
                                    executor.withExecution(
                                        execution.withMetadata(execution.getMetadata().withConcurrencyScopes(concurrencyLimits.stream().map(ScopedConcurrencyLimit::uid).toList())),
                                        "handleConcurrencyLimit"
                                    );
                                }
                            }
                        }

                        // handle execution changed SLA
                        executor = executorService.handleExecutionChangedSLA(executor);

                        // process the execution
                        if (log.isDebugEnabled()) {
                            executorService.log(log, true, executor);
                        }
                        executor = executorService.process(executor);

                        if (executor.getNextCount() > 0) {
                            executor.withExecution(
                                executorService.onNext(executor.getExecution(), executor.getNextCount()),
                                "onNext"
                            );
                        }

                        // worker task
                        if (!executor.getWorkerTasks().isEmpty()) {
                            List<WorkerTaskResult> workerTaskResults = new ArrayList<>();
                            final List<TaskRun> currentTaskRuns = executor.getExecution().getTaskRunList();
                            executor
                                .getWorkerTasks()
                                .forEach(throwConsumer(executorTask ->
                                {
                                    WorkerTask workerTask = executorTask.workerTask();
                                    try {
                                        if (!TruthUtils.isTruthy(executorTask.runContext().render(workerTask.getTask().getRunIf()))) {
                                            workerTaskResults.add(
                                                new WorkerTaskResult(
                                                    workerTask.getTaskRun().withState(State.Type.SKIPPED)
                                                        .addAttempt(TaskRunAttempt.builder().state(new State().withState(State.Type.SKIPPED)).build())
                                                )
                                            );
                                        } else {
                                            if (workerTask.getTask().isSendToWorkerTask()) {
                                                Optional<WorkerQueueRouting> routing = workerGroupService.resolveWorkerQueueForJob(flow, workerTask);
                                                // Internal dispatch convention: null = default queue. SystemTask routing
                                                // is enforced upstream in WorkerQueueService.
                                                String workerQueueId = routing
                                                    .map(WorkerQueueRouting::workerQueueId)
                                                    .map(WorkerQueues::toDispatchKey)
                                                    .orElse(null);
                                                if (workerTask.getTask() instanceof WorkingDirectory) {
                                                    // WorkingDirectory is a flowable so it will be moved to RUNNING a few lines under
                                                    workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTask, workerQueueId));
                                                } else {
                                                    TaskRun taskRun = workerTask.getTaskRun().withState(State.Type.SUBMITTED);
                                                    workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTask.withTaskRun(taskRun), workerQueueId));
                                                    workerTaskResults.add(new WorkerTaskResult(taskRun));
                                                }
                                            }

                                            // flowable attempt state transition to running
                                            // Skip if the task was already terminated by handleChildWorkerTaskResult (e.g., empty Loop)
                                            if (workerTask.getTask().isFlowable() && !workerTask.getTaskRun().getState().isTerminated()) {
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
                                        executorTask.runContext().logger()
                                            .error("Failed to evaluate the runIf condition for task {}. Cause: {}", workerTask.getTask().getId(), e.getMessage(), e);
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
                                .forEach(throwConsumer(subflowExecutionResult ->
                                {
                                    subflowExecutionResultQueue.emit(subflowExecutionResult);
                                }));
                        }

                        // schedulerDelay
                        if (!executor.getExecutionDelays().isEmpty()) {
                            executor.getExecutionDelays()
                                .forEach(executionDelay -> executionDelayStateStore.save(executionDelay));
                        }

                        // subflow executions
                        if (!executor.getSubflowExecutions().isEmpty()) {
                            executor.getSubflowExecutions().forEach(throwConsumer(subflowExecution ->
                            {
                                Execution subExecution = subflowExecution.getExecution();
                                String msg = subExecution.getState().getCurrent() == State.Type.RESTARTED ? String.format(
                                    "Restarted execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]]", subExecution.getId(), subExecution.getFlowId(), subExecution.getNamespace()
                                )
                                    : String.format(
                                        "Created new execution [[link execution=\"%s\" flowId=\"%s\" namespace=\"%s\"]]", subExecution.getId(), subExecution.getFlowId(),
                                        subExecution.getNamespace()
                                    );

                                log.info(msg);

                                // Bind the logger to the parent task so its configured logLevel (e.g. plugin
                                // defaults setting logLevel: WARN) applies to the creation log, which is emitted
                                // directly and therefore bypasses the regular logging pipeline (#16238).
                                Task parentTask = flow.findTaskByTaskIdOrNull(subflowExecution.getParentTaskRun().getTaskId());
                                var logger = parentTask != null
                                    ? runContextLoggerFactory.create(subflowExecution.getParentTaskRun(), parentTask, subflowExecution.getExecution().getKind())
                                    : runContextLoggerFactory.create(execution);
                                logger.emitLogIfEnabled(
                                    LogEntry.of(subflowExecution.getParentTaskRun(), subflowExecution.getExecution().getKind()).toBuilder()
                                        .level(Level.INFO)
                                        .message(msg)
                                        .timestamp(subflowExecution.getParentTaskRun().getState().getStartDate())
                                        .thread(Thread.currentThread().getName())
                                        .build()
                                );

                                executionQueue.emit(subflowExecution.getExecution());
                            }));
                        }

                        // trigger new loop executions
                        if (!executor.getLoopExecutions().isEmpty()) {
                            executor.getLoopExecutions().forEach(throwConsumer(loopExecution -> executionQueue.emit(loopExecution)));
                        }

                        if (executor.getExecution().getState().getCurrent().isTerminatedInError()) {
                            Span.current().setStatus(StatusCode.ERROR, "Execution ended in state " + executor.getExecution().getState().getCurrent().name());
                        }
                        return executor;
                    } catch (QueueException e) {
                        Span.current().recordException(e).setStatus(StatusCode.ERROR);

                        Execution failedExecution = fail(execution, e);
                        return new ExecutorContext(execution).withExecution(failedExecution, "queueException");
                    } catch (FlowNotFoundException e) {
                        // avoid infinite for FlowNotFoundException
                        if (!execution.getState().getCurrent().isFailed()) {
                            Span.current().recordException(e).setStatus(StatusCode.ERROR);
                            Execution failedExecution = fail(execution, e);
                            return new ExecutorContext(execution).withExecution(failedExecution, "flowNotFound");
                        }

                        Span.current().recordException(e).setStatus(StatusCode.ERROR);
                        return null;
                    }
                }
            )
        );
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        var logger = runContextLoggerFactory.create(failedExecution.execution());
        logger.emitLogs(failedExecution.logs());
        return failedExecution.execution().getState().isFailed() ? failedExecution.execution() : failedExecution.execution().withState(State.Type.FAILED);
    }

}
