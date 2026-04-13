package io.kestra.executor.handler;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import java.io.IOException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.TerminatedLoopExecution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.*;
import io.kestra.plugin.core.flow.Loop;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@Slf4j
public class TerminatedLoopExecutionMessageHandler implements ExecutorMessageHandler<TerminatedLoopExecution> {
    @Inject
    private ExecutorService executorService;

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionStateStore executionStateStore;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Override
    public Optional<ExecutorContext> handle(TerminatedLoopExecution message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        return executionStateStore.lock(message.loopRun().executionId(), execution ->
        {
            try {
                final FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                ExecutorContext executor = new ExecutorContext(execution, flow);
                TaskRun parentTaskRun = execution.findTaskRunByTaskRunId(message.loopRun().taskRunId());
                Loop loop = (Loop) executor.getFlow().findTaskByTaskId(message.loopRun().taskId());

                if (loop.getTransmitFailed() && message.state().isTerminatedInError()) {
                    // immediately terminate the loop
                    return terminateLoop(parentTaskRun, loop, executor, message.state());
                } else {
                    // increment iteration
                    Map<String, Object> outputs = taskOutputService.getOutputs(parentTaskRun);
                    int iterationCount = (Integer) outputs.get(Loop.ITERATION_COUNT_OUTPUT);
                    int runningIteration = (Integer) outputs.get(Loop.RUNNING_ITERATIONS_OUTPUT) - 1;
                    int terminatedIteration = (Integer) outputs.get(Loop.TERMINATED_ITERATIONS_OUTPUT) + 1;

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
                            taskOutputService.saveOutputs(parentTaskRun, Map.of(
                                Loop.ITERATION_COUNT_OUTPUT, iterationCount,
                                Loop.RUNNING_ITERATIONS_OUTPUT, runningIteration + 1,
                                Loop.TERMINATED_ITERATIONS_OUTPUT, terminatedIteration,
                                Loop.NEXT_OFFSET_OUTPUT, valuesAndOffset.getRight())
                            );
                            var loopExecution = executor.getExecution().loopExecution(IdUtils.create(), parentTaskRun, nextIndex, null, value);
                            executionQueue.emit(loopExecution);
                        } else {
                            // Non-URI mode: resolve all values in memory and pick by index
                            taskOutputService.saveOutputs(parentTaskRun, Map.of(
                                Loop.ITERATION_COUNT_OUTPUT, iterationCount,
                                Loop.RUNNING_ITERATIONS_OUTPUT, runningIteration + 1,
                                Loop.TERMINATED_ITERATIONS_OUTPUT, terminatedIteration)
                            );
                            var either = FlowableUtils.resolveValues(runContext, loop.getValues());
                            if (either.isLeft()) {
                                String value = either.getLeft().get(nextIndex);
                                var loopExecution = executor.getExecution().loopExecution(IdUtils.create(), parentTaskRun, nextIndex, null, value);
                                executionQueue.emit(loopExecution);
                            } else {
                                Pair<String, String> value = either.getRight().get(nextIndex);
                                var loopExecution = executor.getExecution().loopExecution(IdUtils.create(), parentTaskRun, nextIndex, value.getKey(), value.getValue());
                                executionQueue.emit(loopExecution);
                            }
                        }
                        // we don't update the execution itself as the loop is still running
                        // TODO we may need to send a follow execution message to update the UI
                        return null;
                    } else {
                        // All iterations have been started — save the decremented counts and either
                        // terminate (if all are done) or wait for the remaining in-flight ones.
                        taskOutputService.saveOutputs(parentTaskRun, Map.of(
                            Loop.ITERATION_COUNT_OUTPUT, iterationCount,
                            Loop.RUNNING_ITERATIONS_OUTPUT, runningIteration,
                            Loop.TERMINATED_ITERATIONS_OUTPUT, terminatedIteration)
                        );
                        if (terminatedIteration == iterationCount) {
                            // All iterations have completed — end the loop with success.
                            return terminateLoop(parentTaskRun, loop, executor, State.Type.SUCCESS);
                        } else {
                            // Some iterations are still running — wait for them.
                            // TODO we may need to send a follow execution message to update the UI
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
