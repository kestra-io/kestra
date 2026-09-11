package io.kestra.executor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.ExecutionTerminatedNotifier;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.Logs;
import io.kestra.executor.handler.ExecutionCommandMessageHandler;
import io.kestra.executor.handler.ExecutionEventMessageHandler;
import io.kestra.executor.handler.ExecutionKilledExecutionMessageHandler;
import io.kestra.executor.handler.LoopExecutionEventMessageHandler;
import io.kestra.executor.handler.MultipleConditionEventMessageHandler;
import io.kestra.executor.handler.SubflowExecutionEndMessageHandler;
import io.kestra.executor.handler.SubflowExecutionResultMessageHandler;
import io.kestra.executor.handler.WorkerTaskResultMessageHandler;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.flow.Subflow;

import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwRunnable;

/**
 * The executor state machine, callable: one inbound message in through the same entry points
 * {@code DefaultExecutor} feeds from its queues, the resulting messages out on the injected queues.
 * Every body is a verbatim move from {@code DefaultExecutor}; the class exists so the machine can
 * be constructed and stepped without the service, its thread pools or a broker.
 */
@Singleton
@Slf4j
public class ExecutorCore {
    private final ExecutorService executorService;
    private final ExecutionService executionService;
    private final FlowTriggerService flowTriggerService;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ExecutionStateStore executionStateStore;
    private final SLAMonitorStateStore slaMonitorStateStore;
    private final ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor;
    private final ExecutionDelayProcessor executionDelayProcessor;
    private final SLAMonitorProcessor slaMonitorProcessor;
    private final RunContextFactory runContextFactory;
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final DispatchQueueInterface<Execution> executionQueue;
    private final DispatchQueueInterface<ExecutionEvent> executionEventQueue;
    private final BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue;
    private final DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue;
    private final DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue;
    private final DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue;
    private final DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue;
    private final TriggerEventQueue triggerEventQueue;
    private final ExecutionTerminatedNotifier executionTerminatedNotifier;
    private final ExecutionCommandMessageHandler executionCommandMessageHandler;
    private final ExecutionEventMessageHandler executionEventMessageHandler;
    private final WorkerTaskResultMessageHandler workerTaskResultMessageHandler;
    private final ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;
    private final SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;
    private final SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;
    private final MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;
    private final LoopExecutionEventMessageHandler loopExecutionEventMessageHandler;
    private final Timer flowTriggerProcessingTimer;
    private final Clock clock;

    @Inject
    public ExecutorCore(
        ExecutorService executorService,
        ExecutionService executionService,
        FlowTriggerService flowTriggerService,
        FlowMetaStoreInterface flowMetaStore,
        ExecutionStateStore executionStateStore,
        SLAMonitorStateStore slaMonitorStateStore,
        ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor,
        ExecutionDelayProcessor executionDelayProcessor,
        SLAMonitorProcessor slaMonitorProcessor,
        RunContextFactory runContextFactory,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        MetricRegistry metricRegistry,
        Clock clock,
        DispatchQueueInterface<Execution> executionQueue,
        DispatchQueueInterface<ExecutionEvent> executionEventQueue,
        BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue,
        DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue,
        DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue,
        DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue,
        DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue,
        TriggerEventQueue triggerEventQueue,
        ExecutionTerminatedNotifier executionTerminatedNotifier,
        ExecutionCommandMessageHandler executionCommandMessageHandler,
        ExecutionEventMessageHandler executionEventMessageHandler,
        WorkerTaskResultMessageHandler workerTaskResultMessageHandler,
        ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler,
        SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler,
        SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler,
        MultipleConditionEventMessageHandler multipleConditionEventMessageHandler,
        LoopExecutionEventMessageHandler loopExecutionEventMessageHandler) {
        this.executorService = executorService;
        this.executionService = executionService;
        this.flowTriggerService = flowTriggerService;
        this.flowMetaStore = flowMetaStore;
        this.executionStateStore = executionStateStore;
        this.slaMonitorStateStore = slaMonitorStateStore;
        this.concurrencySlotReleaseProcessor = concurrencySlotReleaseProcessor;
        this.executionDelayProcessor = executionDelayProcessor;
        this.slaMonitorProcessor = slaMonitorProcessor;
        this.runContextFactory = runContextFactory;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
        this.executionQueue = executionQueue;
        this.executionEventQueue = executionEventQueue;
        this.followExecutionEventQueue = followExecutionEventQueue;
        this.subflowExecutionEndQueue = subflowExecutionEndQueue;
        this.multipleConditionEventQueue = multipleConditionEventQueue;
        this.loopExecutionEventQueue = loopExecutionEventQueue;
        this.executionStatisticQueue = executionStatisticQueue;
        this.triggerEventQueue = triggerEventQueue;
        this.executionTerminatedNotifier = executionTerminatedNotifier;
        this.executionCommandMessageHandler = executionCommandMessageHandler;
        this.executionEventMessageHandler = executionEventMessageHandler;
        this.workerTaskResultMessageHandler = workerTaskResultMessageHandler;
        this.executionKilledExecutionMessageHandler = executionKilledExecutionMessageHandler;
        this.subflowExecutionResultMessageHandler = subflowExecutionResultMessageHandler;
        this.subflowExecutionEndMessageHandler = subflowExecutionEndMessageHandler;
        this.multipleConditionEventMessageHandler = multipleConditionEventMessageHandler;
        this.loopExecutionEventMessageHandler = loopExecutionEventMessageHandler;
        this.clock = clock;
        this.flowTriggerProcessingTimer = metricRegistry
            .timer(MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION, MetricRegistry.METRIC_EXECUTOR_FLOW_TRIGGER_PROCESSING_DURATION_DESCRIPTION);
    }

    // --- one step per inbound message kind: what DefaultExecutor's queue callbacks do once deserialized

    public void onExecution(Execution execution) {
        // Always persist first so the execution is present in the DB even if kill-switched.
        try {
            executionStateStore.create(execution);
        } catch (Exception e) {
            log.error("Unable to create execution {}", execution.getId(), e);
        }

        EvaluationType evaluationType = killSwitchService.evaluate(execution);
        if (evaluationType.isKillSwitched(execution)) {
            killSwitchActionService.handle(evaluationType, execution.getTenantId(), execution.getId());
            return;
        }

        var eventType = execution.getState().isCreated() ? ExecutionEventType.CREATED : ExecutionEventType.UPDATED;
        executionEventMessageHandler.handle(new ExecutionEvent(execution, eventType)).ifPresent(this::toExecution);
    }

    public void onExecutionCommand(ExecutionCommand command) {
        executionCommandMessageHandler.handle(command).ifPresent(this::toExecution);
    }

    public void onExecutionEvent(ExecutionEvent event) {
        executionEventMessageHandler.handle(event).ifPresent(this::toExecution);
    }

    public void onWorkerTaskResult(WorkerTaskResult result) {
        workerTaskResultMessageHandler.handle(result).ifPresent(this::toExecution);
    }

    public void onExecutionKilled(ExecutionKilled event) {
        // Ignore a kill event when it's already executed
        if (event.getState() == ExecutionKilled.State.EXECUTED) {
            return;
        }
        // Only handle ExecutionKilledExecution here (not ExecutionKilledTrigger)
        if (!(event instanceof ExecutionKilledExecution killedExecution)) {
            return;
        }
        executionKilledExecutionMessageHandler.handle(killedExecution).ifPresent(executor -> this.toExecution(executor, true));
    }

    public void onSubflowExecutionResult(SubflowExecutionResult result) {
        subflowExecutionResultMessageHandler.handle(result).ifPresent(this::toExecution);
    }

    public void onSubflowExecutionEnd(SubflowExecutionEnd end) {
        subflowExecutionEndMessageHandler.handle(end);
    }

    public void onMultipleConditionEvent(MultipleConditionEvent event) {
        multipleConditionEventMessageHandler.handle(event);
    }

    public void onLoopExecutionEvent(LoopExecutionEvent event) {
        loopExecutionEventMessageHandler.handle(event).ifPresent(this::toExecution);
    }

    // --- the two timer-driven steps

    public void onExpiredExecutionDelays(Instant now) {
        executionDelayProcessor.processExpired(now).forEach(this::toExecution);
    }

    public void onExpiredSLAMonitors(Instant now) {
        slaMonitorProcessor.processExpired(now).forEach(this::toExecution);
    }

    // --- verbatim from DefaultExecutor

    private void toExecution(ExecutorContext executor) {
        toExecution(executor, false);
    }

    private void toExecution(ExecutorContext executor, boolean ignoreFailure) {
        try {
            boolean shouldSend = false;

            if (executor.getException() != null) {
                executor = executorService.handleFailedExecutionFromExecutor(executor, executor.getException());
                shouldSend = true;
            } else if (executor.isExecutionUpdated()) {
                shouldSend = true;
            }

            if (!shouldSend) {
                Execution execution = executor.getExecution();

                // purge the trigger: reset scheduler trigger at end
                // IMPORTANT: this is to cover an edge case, execution created for failed trigger didn't have any taskrun so they will arrive directly here.
                // We need to detect that and reset them as they will never reach the reset code later on this method.
                if (
                    execution.getTrigger() != null &&
                        (execution.getState().isFailed() || execution.getState().getCurrent().isKilled() || execution.getState().getCurrent().isCancelled()) &&
                        ListUtils.isEmpty(execution.getTaskRunList())
                ) {
                    sendTriggerExecutionTerminated(executor.getFlow(), execution);
                    this.followExecutionEventQueue.emit(new FollowExecutionEvent(execution, ExecutionEventType.TERMINATED));
                    emitExecutionStatistic(execution);
                    notifyExecutionTerminated(execution);
                }

                return;
            }

            if (log.isDebugEnabled()) {
                executorService.log(log, false, executor);
            }

            // the terminated state can come from the execution queue, in this case we always have a flow in the executor
            // or from a worker task in an afterExecution block, in this case we need to load the flow
            if (executor.getFlow() == null && executor.getExecution().getState().isTerminated()) {
                var execution = executor.getExecution();
                FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                executor = executor.withFlow(flow);
            }
            boolean isTerminated = executor.getFlow() != null && executionService.isTerminated(executor.getFlow(), executor.getExecution());

            Execution execution = executor.getExecution();
            // Fire flow triggers for every distinct state transition that occurred in this cycle.
            // A single cycle can advance through multiple states (e.g. PAUSED → RUNNING → SUCCESS
            // when a pause-resume delay fires and the executor immediately completes the next task).
            // Iterating stateTransitions[1..n] ensures each intermediate state reaches the trigger
            // pipeline, regardless of how many transitions collapsed into one executor cycle.
            List<State.Type> transitions = executor.getStateTransitions();
            for (int i = 1; i < transitions.size(); i++) {
                State.Type transitionState = transitions.get(i);
                processFlowTriggers(transitionState == execution.getState().getCurrent() ? execution : execution.withState(transitions.get(i)));
            }

            // IMPORTANT: this must be done before emitting the last execution message so that all consumers are notified that the execution ends.
            if (isTerminated) {
                // release the concurrency slots (a no-op when no limit applies to the flow),
                // then check if there exists a queued execution and submit it to the execution queue.
                // Transactional outbox: the processor pops inside the concurrency-limit
                // store's transaction and only returns the execution; it is emitted here,
                // after releaseThenPop() has committed (same rule as executionDelayLoop).
                // This runs first in the terminal block on purpose: only the cycle that terminated
                // the execution may release.
                // An execution that was not already terminal when this cycle started cannot have
                // been over before it: afterExecution tasks run once the execution state is already terminal,
                // and the cycle completing them is the one that really terminates the execution.
                Execution executionAtEntry = executor.getTerminalExecutionAtEntry();
                boolean terminatedByThisCycle = executionAtEntry == null
                    || !executionService.isTerminated(executor.getFlow(), executionAtEntry);
                Optional<Execution> popped = concurrencySlotReleaseProcessor.release(executor, terminatedByThisCycle);
                if (popped.isPresent()) {
                    executionQueue.emit(popped.get());

                    // process flow triggers to allow listening on RUNNING state after a QUEUED state
                    processFlowTriggers(popped.get());
                }

                // if there is a parent, we send a subflow execution result to it
                if (ExecutableUtils.isSubflow(execution)) {
                    // locate the parent execution to find the parent task run
                    String parentExecutionId = (String) execution.getTrigger().getVariables().get("executionId");
                    String taskRunId = (String) execution.getTrigger().getVariables().get("taskRunId");
                    String taskId = (String) execution.getTrigger().getVariables().get("taskId");
                    SubflowExecutionEnd subflowExecutionEnd = new SubflowExecutionEnd(executor.getExecution(), parentExecutionId, taskRunId, taskId, execution.getState().getCurrent());
                    this.subflowExecutionEndQueue.emit(subflowExecutionEnd);
                }

                // if it was a loop execution, we send a terminated loop execution message to the parent execution
                if (executor.getExecution().getKind() == ExecutionKind.LOOP) {
                    var loop = (Loop) executor.getFlow().findTaskByTaskId(executor.getExecution().getLoopRun().taskId());
                    Map<String, Object> outputs = null;
                    if (!ListUtils.isEmpty(loop.getOutputs())) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), executor.getExecution());
                        try {
                            outputs = loop.computeIterationOutput(runContext, execution);
                        } catch (Exception e) {
                            Logs.logExecution(
                                executor.getExecution(),
                                Level.ERROR,
                                "Failed to render output values",
                                e
                            );
                            runContext.logger().error("Failed to render output values: {}", e.getMessage(), e);
                            execution = execution.withState(State.Type.FAILED);
                            // Persist the FAILED state so the sub-execution is correctly reflected in the DB.
                            try {
                                executionStateStore.lock(
                                    execution.getId(), exec -> new ExecutorContext(exec).withExecution(exec.withState(State.Type.FAILED), "failedOutputRender")
                                );
                            } catch (Exception persistException) {
                                log.error("Failed to persist FAILED state for loop sub-execution {}", execution.getId(), persistException);
                            }
                            executor = executor.withExecution(execution, "failedOutputRender");
                        }

                    }
                    loopExecutionEventQueue.emit(new LoopExecutionEvent(execution.getLoopRun(), execution.getId(), execution.getState().getCurrent(), outputs));
                }

                // purge SLA monitors
                if (!ListUtils.isEmpty(executor.getFlow().getSla()) && executor.getFlow().getSla().stream().anyMatch(ExecutionMonitoringSLA.class::isInstance)) {
                    slaMonitorStateStore.purge(executor.getExecution().getId());
                }

                // purge the trigger: reset scheduler trigger at end
                if (execution.getTrigger() != null && !isRealtimeTriggerExecution(executor.getFlow(), execution)) {
                    sendTriggerExecutionTerminated(executor.getFlow(), execution);
                }

                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                // Note that we must use 'emit' here and not emitAsync as we need to emit it inside the same transaction to avoid races,
                // and transactions are bound to a thread. This is true for all emission of the follow execution event inside an execution lock.
                this.followExecutionEventQueue.emit(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.TERMINATED));

                emitExecutionStatistic(execution);
                notifyExecutionTerminated(execution);
            } else {
                ExecutionEvent event = new ExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED);
                this.executionEventQueue.emit(event);

                // update all execution followers
                this.followExecutionEventQueue.emit(new FollowExecutionEvent(executor.getExecution(), ExecutionEventType.UPDATED));
            }
        } catch (QueueException | FlowNotFoundException | InternalException e) {
            if (!ignoreFailure) {
                // If we cannot add the new worker task result to the execution, we fail it.
                // Persist the FAILED state first, then emit the queue events
                // only after the transaction commits to avoid potential race conditions inside the follow endpoint.
                Optional<ExecutorContext> failedExecutorOpt = executionStateStore.lock(
                    executor.getExecution().getId(), execution ->
                    {
                        Execution failed = execution.failedExecutionFromExecutor(e).execution().withState(State.Type.FAILED);
                        return new ExecutorContext(execution).withExecution(failed, "toExecutionFailure");
                    }
                );

                if (failedExecutorOpt.isPresent()) {
                    Execution failedExecution = failedExecutorOpt.get().getExecution();
                    try {
                        this.executionEventQueue.emit(new ExecutionEvent(failedExecution, ExecutionEventType.TERMINATED));

                        // update all execution followers
                        this.followExecutionEventQueue.emit(new FollowExecutionEvent(failedExecution, ExecutionEventType.TERMINATED));

                        emitExecutionStatistic(failedExecution);
                        notifyExecutionTerminated(failedExecution);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the execution {}", failedExecution.getId(), ex);
                    }
                }
            }
        }
    }

    /**
     * Asynchronously emits a raw execution-statistic row for the indexer to persist for every terminal NORMAL-kind execution.
     */

    private void emitExecutionStatistic(Execution execution) {
        if (ExecutionKind.isNormal(execution)) {
            // An end date should always be set, but use the current date as a safety belt
            Instant bucket = execution.getState().getEndDate().orElse(clock.instant()).truncatedTo(ChronoUnit.MINUTES);
            this.executionStatisticQueue.emitAsync(new ExecutionStatistic(execution, bucket));
        }
    }

    private void notifyExecutionTerminated(Execution execution) {
        try {
            this.executionTerminatedNotifier.executionTerminated(execution);
        } catch (Exception e) {
            log.warn("Unable to notify execution terminated for execution '{}'", execution.getId(), e);
        }
    }

    /**
     * Sends {@link TriggerExecutionTerminated}, unless the scheduler does not evaluate the trigger: those hold a
     * state but take no lock to release, so the termination would only apply {@code stopAfter} and disable them.
     * A trigger the flow no longer declares resolves to none, as does a subflow, which records the parent task
     * rather than a trigger.
     */
    private void sendTriggerExecutionTerminated(FlowWithSource flow, Execution execution) {
        if (shouldReleaseTriggerLock(flow, execution)) {
            TriggerId triggerId = TriggerId.of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getTrigger().getId());
            triggerEventQueue.send(new TriggerExecutionTerminated(triggerId, execution.getId(), execution.getState().getCurrent()));
        }
    }

    /**
     * A realtime trigger's lock spans the trigger's whole lifetime on the worker, not a single execution.
     * Terminations of the executions it emits must not send {@link TriggerExecutionTerminated}, otherwise the
     * scheduler would unlock and resubmit a trigger that is still running. The trigger-creation failure path
     * (FAILED execution with no task run) bypasses this check and remains the termination signal.
     */
    static boolean isRealtimeTriggerExecution(FlowWithSource flow, Execution execution) {
        if (flow == null || flow.getTriggers() == null) {
            return false;
        }
        for (AbstractTrigger trigger : flow.getTriggers()) {
            if (trigger.getId().equals(execution.getTrigger().getId())) {
                return TriggerType.REALTIME.equals(TriggerType.from(trigger));
            }
        }
        return false;
    }

    /**
     * Returns whether the termination of this execution should release its trigger's lock.
     * <p>
     * Only the triggers the scheduler evaluates take one. A trigger that resolves to one it does not evaluate is
     * skipped, since the termination would only apply that trigger's {@code stopAfter}; a subflow records the
     * parent task rather than a trigger, so it has no state at all. A trigger that cannot be resolved falls back
     * to releasing — a flow that no longer parses carries no triggers, and a lock left held would stop the
     * trigger from ever being scheduled again.
     */
    static boolean shouldReleaseTriggerLock(FlowWithSource flow, Execution execution) {
        if (Subflow.class.getName().equals(execution.getTrigger().getType())) {
            return false;
        }
        return ListUtils.emptyOnNull(flow == null ? null : flow.getTriggers()).stream()
            .filter(trigger -> trigger.getId().equals(execution.getTrigger().getId()))
            .findFirst()
            .map(trigger -> TriggerType.isEvaluatedByScheduler(TriggerType.from(trigger)))
            .orElse(true);
    }

    private void processFlowTriggers(Execution execution) throws QueueException {
        flowTriggerProcessingTimer.record(throwRunnable(() ->
        {
            Collection<FlowWithSource> allFlows = flowMetaStore.allLastVersion();

            // directly process simple conditions
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> f.getFlow())
                .distinct() // as computeExecutionsFromFlowTriggers is based on flow, we must map FlowWithFlowTrigger to a flow and distinct to avoid multiple execution for the same flow
                .flatMap(f -> flowTriggerService.computeExecutionsFromFlowTriggerConditions(execution, f).stream())
                .forEach(throwConsumer(executionQueue::emit));

            // send multiple conditions to the multiple condition queue for later processing
            flowTriggerService.withFlowTriggersOnly(allFlows.stream())
                .filter(f -> !ListUtils.isEmpty(f.getTrigger().getDependsOn()))
                .map(f -> new MultipleConditionEvent(f.getFlow(), execution))
                .distinct() // we can have multiple MultipleConditionEvent if a flow contains multiple triggers as it would lead to multiple FlowWithFlowTrigger
                .forEach(throwConsumer(multipleCondition -> multipleConditionEventQueue.emit(multipleCondition)));
        }));
    }
}
