package io.kestra.executor.handler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.executions.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.executor.KillSwitchActionService;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.executor.*;
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
    @Inject
    private ExecutionStateStore executionStateStore;
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;
    @Inject
    private ExecutionDelayStateStore executionDelayStateStore;
    @Inject
    private SLAMonitorStateStore slaMonitorStateStore;
    @Inject
    private ConcurrencyLimitStateStore concurrencyLimitStateStore;

    @Inject
    private ExecutorService executorService;
    @Inject
    private WorkerQueueService workerGroupService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    @Inject
    private DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;
    @Inject
    private DispatchQueueInterface<Execution> executionQueue;
    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private KillSwitchService killSwitchService;
    @Inject
    private KillSwitchActionService killSwitchActionService;

    private final Tracer tracer;

    @Inject
    public ExecutionEventMessageHandler(TracerFactory tracerFactory) {
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
                        final FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
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

                        // create an SLA monitor if needed
                        if ((execution.getState().getCurrent() == State.Type.CREATED || execution.getState().failedThenRestarted()) && !ListUtils.isEmpty(flow.getSla())) {
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

                            ExecutionRunning processed = concurrencyLimitStateStore.countThenProcess(flow, (txContext, concurrencyLimit) ->
                            {
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

                                var logger = runContextLoggerFactory.create(execution);
                                logger.emitLog(
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

                        return executor;
                    } catch (QueueException e) {
                        Span.current().recordException(e).setStatus(StatusCode.ERROR);

                        Execution failedExecution = fail(execution, e);
                        return new ExecutorContext(execution).withExecution(failedExecution, "queueException");
                    } catch (FlowNotFoundException e) {
                        // avoid infinite for FlowNotFoundException
                        if (!execution.getState().getCurrent().isFailed()) {
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
