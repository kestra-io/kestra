package io.kestra.executor.handler;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.executor.*;
import io.kestra.plugin.core.flow.Loop;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@link LoopExecutionEvent} messages, propagating loop sub-execution state changes
 * (terminated or paused) to the parent execution.
 */
@Singleton
@Slf4j
public class LoopExecutionEventMessageHandler implements ExecutorMessageHandler<LoopExecutionEvent> {
    private final ExecutorService executorService;
    private final ExecutionService executionService;
    private final TaskOutputService taskOutputService;
    private final ExecutionStateStore executionStateStore;
    private final RunContextFactory runContextFactory;
    private final FlowMetaStoreInterface flowMetaStore;
    private final DispatchQueueInterface<Execution> executionQueue;
    private final BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue;
    private final KillSwitchService killSwitchService;
    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public LoopExecutionEventMessageHandler(
        ExecutorService executorService,
        ExecutionService executionService,
        TaskOutputService taskOutputService,
        ExecutionStateStore executionStateStore,
        RunContextFactory runContextFactory,
        FlowMetaStoreInterface flowMetaStore,
        DispatchQueueInterface<Execution> executionQueue,
        BroadcastQueueInterface<FollowExecutionEvent> followExecutionEventQueue,
        KillSwitchService killSwitchService,
        RunContextLoggerFactory runContextLoggerFactory) {
        this.executorService = executorService;
        this.executionService = executionService;
        this.taskOutputService = taskOutputService;
        this.executionStateStore = executionStateStore;
        this.runContextFactory = runContextFactory;
        this.flowMetaStore = flowMetaStore;
        this.executionQueue = executionQueue;
        this.followExecutionEventQueue = followExecutionEventQueue;
        this.killSwitchService = killSwitchService;
        this.runContextLoggerFactory = runContextLoggerFactory;
    }

    @Override
    public Optional<ExecutorContext> handle(LoopExecutionEvent message) {
        if (killSwitchService.evaluate(message.executionId()) != EvaluationType.PASS) {
            log.warn("Ignoring loop execution event for sub-execution {} as there is a kill switch on it", message.executionId());
            return Optional.empty();
        }
        if (killSwitchService.evaluate(message.loopRun().parent().getId()) != EvaluationType.PASS) {
            log.warn("Ignoring loop execution event for parent execution {} as there is a kill switch on it", message.loopRun().parent().getId());
            return Optional.empty();
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        if (message.state().isPaused()) {
            return handlePaused(message);
        }
        return handleTerminated(message);
    }

    private Optional<ExecutorContext> handleTerminated(LoopExecutionEvent message) {
        return executionStateStore.lock(message.loopRun().parent().getId(), execution ->
        {
            try {
                final FlowWithSource flow = flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                ExecutorContext executor = new ExecutorContext(execution, flow);
                TaskRun parentTaskRun = execution.findTaskRunByTaskRunId(message.loopRun().taskRunId());
                Loop loop = (Loop) executor.getFlow().findTaskByTaskId(message.loopRun().taskId());

                if (loop.getTransmitFailed() && message.state().isTerminatedInError()) {
                    // the failure happened inside an isolated loop sub-execution: log inside the parent exec
                    logLoopIterationFailure(parentTaskRun, loop, executor, message);
                    // immediately terminate the loop
                    return terminateLoop(parentTaskRun, loop, executor, message.state());
                } else {
                    // increment iteration
                    Map<String, Object> outputs = taskOutputService.getOutputs(parentTaskRun);
                    int iterationCount = (Integer) outputs.get(Loop.ITERATION_COUNT_OUTPUT);
                    int runningIteration = (Integer) outputs.get(Loop.RUNNING_ITERATIONS_OUTPUT) - 1;
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> terminatedByState = outputs.containsKey(Loop.TERMINATED_ITERATIONS_OUTPUT)
                        ? (Map<String, Integer>) outputs.get(Loop.TERMINATED_ITERATIONS_OUTPUT)
                        : HashMap.newHashMap(6);
                    terminatedByState.merge(message.state().name(), 1, Integer::sum);
                    int terminatedIteration = terminatedByState.values().stream().mapToInt(Integer::intValue).sum();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> taskOutputs = outputs.containsKey(Loop.OUTPUTS_OUTPUT) ? (List<Map<String, Object>>) outputs.get(Loop.OUTPUTS_OUTPUT) : new ArrayList<>();
                    if (!MapUtils.isEmpty(message.outputs())) {
                        taskOutputs.add(buildIterationOutput(message));
                    }

                    // Check the next iteration index
                    int nextIndex = runningIteration + terminatedIteration;
                    if (nextIndex < iterationCount) {
                        RunContext runContext = runContextFactory.of(executor.getFlow(), loop, executor.getExecution(), parentTaskRun);
                        if (outputs.containsKey(Loop.NEXT_OFFSET_OUTPUT)) {
                            // URI mode: seek to stored offset and read the next value
                            long nextOffset = ((Number) outputs.get(Loop.NEXT_OFFSET_OUTPUT)).longValue();
                            String valuesUri = FlowableUtils.resolveLoopValuesUri(runContext, loop.getValues())
                                .orElseThrow(() -> new IllegalStateException("Loop has a nextOffset output but values did not resolve to a URI"));
                            var valuesAndOffset = FlowableUtils.readLoopValuesFromUri(runContext, valuesUri, nextOffset, 1);
                            String value = valuesAndOffset.getLeft().getFirst();
                            computeOutputs(parentTaskRun, taskOutputs, iterationCount, runningIteration + 1, terminatedByState, valuesAndOffset.getRight());
                            var loopExecution = executor.getExecution().loopExecution(parentTaskRun, nextIndex, null, value);
                            executionQueue.emit(loopExecution);
                        } else {
                            // Non-URI mode: resolve all values in memory and pick by index
                            computeOutputs(parentTaskRun, taskOutputs, iterationCount, runningIteration + 1, terminatedByState, null);
                            var either = FlowableUtils.resolveValues(runContext, loop.getValues());
                            if (either.isLeft()) {
                                String value = either.getLeft().get(nextIndex);
                                var loopExecution = executor.getExecution().loopExecution(parentTaskRun, nextIndex, null, value);
                                executionQueue.emit(loopExecution);
                            } else {
                                Pair<String, String> value = either.getRight().get(nextIndex);
                                var loopExecution = executor.getExecution().loopExecution(parentTaskRun, nextIndex, value.getKey(), value.getValue());
                                executionQueue.emit(loopExecution);
                            }
                        }

                        // we don't update the execution itself as the loop is still running, but we send a follow execution event to update the UI
                        followExecutionEventQueue.emit(new FollowExecutionEvent(execution, ExecutionEventType.UPDATED));
                        return null;
                    } else {
                        // All iterations have been started — save the decremented counts and either
                        // terminate (if all are done) or wait for the remaining in-flight ones.
                        computeOutputs(parentTaskRun, taskOutputs, iterationCount, runningIteration, terminatedByState, null);
                        if (terminatedIteration == iterationCount) {
                            // All iterations have completed — end the loop with success.
                            return terminateLoop(parentTaskRun, loop, executor, State.Type.SUCCESS);
                        } else {
                            // Some iterations are still running — wait for them.
                            // we don't update the execution itself as the loop is still running, but we send a follow execution event to update the UI
                            followExecutionEventQueue.emit(new FollowExecutionEvent(execution, ExecutionEventType.UPDATED));
                            return null;
                        }
                    }
                }
            } catch (InternalException | QueueException | IOException e) {
                return executorService.handleFailedExecutionFromExecutor(new ExecutorContext(execution), e);
            } catch (FlowNotFoundException e) {
                // avoid infinite loop for FlowNotFoundException
                if (!execution.getState().getCurrent().isFailed()) {
                    return executorService.handleFailedExecutionFromExecutor(new ExecutorContext(execution), e);
                }

                return null;
            }
        });
    }

    private Optional<ExecutorContext> handlePaused(LoopExecutionEvent message) {
        return executionStateStore.lock(message.loopRun().parent().getId(), execution ->
        {
            try {
                ExecutorContext executor = new ExecutorContext(execution);
                // throws InternalException if not found — treated as a hard failure below
                TaskRun loopTaskRun = execution.findTaskRunByTaskRunId(message.loopRun().taskRunId());

                Execution pausedExecution = executionService.pauseFlowable(execution, loopTaskRun);

                return executor.withExecution(pausedExecution, "pausedLoopIteration");
            } catch (InternalException e) {
                return executorService.handleFailedExecutionFromExecutor(new ExecutorContext(execution), e);
            }
        });
    }

    private void logLoopIterationFailure(TaskRun parentTaskRun, Loop loop, ExecutorContext executor, LoopExecutionEvent message) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(parentTaskRun, loop, executor.getExecution().getKind());
        runContextLogger.logger().error(
            "Loop iteration {} ended in {} in sub-execution [[link execution=\"{}\" flowId=\"{}\" namespace=\"{}\"]], check that execution's logs for details",
            message.loopRun().index(),
            message.state(),
            message.executionId(),
            executor.getExecution().getFlowId(),
            executor.getExecution().getNamespace()
        );
    }

    private Map<String, Object> buildIterationOutput(LoopExecutionEvent message) {
        Map<String, Object> item = HashMap.newHashMap(3);
        item.put("value", message.loopRun().value());
        item.put("iteration", message.loopRun().index());
        if (message.loopRun().key() != null) {
            item.put("key", message.loopRun().key());
        }
        return Map.of(
            "item", item,
            "outputs", message.outputs()
        );
    }

    private void computeOutputs(TaskRun parentTaskRun, List<Map<String, Object>> taskOutputs, Integer iterationCount, Integer runningIteration, Map<String, Integer> terminatedByState,
        Long offset)
        throws InternalException {
        Map<String, Object> outputs = taskOutputService.getOutputs(parentTaskRun);
        outputs.put(Loop.ITERATION_COUNT_OUTPUT, iterationCount);
        outputs.put(Loop.RUNNING_ITERATIONS_OUTPUT, runningIteration);
        outputs.put(Loop.TERMINATED_ITERATIONS_OUTPUT, terminatedByState);
        if (offset != null) {
            outputs.put(Loop.NEXT_OFFSET_OUTPUT, offset);
        }
        if (!ListUtils.isEmpty(taskOutputs)) {
            outputs.put(Loop.OUTPUTS_OUTPUT, taskOutputs);
        }
        taskOutputService.saveOutputs(parentTaskRun, outputs);
    }

    // terminate the loop and its attempts
    private ExecutorContext terminateLoop(TaskRun parentTaskRun, Task task, final ExecutorContext executor, State.Type state) throws InternalException {
        State.Type finalState = state == State.Type.FAILED ? stateFailure(task) : State.Type.SUCCESS;
        List<TaskRunAttempt> attempts = Optional.ofNullable(parentTaskRun.getAttempts())
            .map(ArrayList::new)
            .orElseGet(ArrayList::new);
        TaskRunAttempt updated = attempts.getLast().withState(finalState);
        attempts.set(attempts.size() - 1, updated);
        TaskRun newTaskRun = parentTaskRun.withState(finalState)
            .withAttempts(attempts);
        WorkerTaskResult workerTaskResult = new WorkerTaskResult(newTaskRun);
        executorService.addWorkerTaskResult(executor, () -> executor.getFlow(), workerTaskResult);
        return executor;
    }

    private State.Type stateFailure(Task task) {
        return task.isAllowFailure() ? (task.isAllowWarning() ? State.Type.SUCCESS : State.Type.WARNING) : State.Type.FAILED;
    }
}
