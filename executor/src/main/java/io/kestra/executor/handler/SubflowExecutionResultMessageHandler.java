package io.kestra.executor.handler;

import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.*;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.MapUtils;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;
import io.kestra.executor.ExecutorService;
import io.kestra.plugin.core.flow.ForEachItem;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SubflowExecutionResultMessageHandler implements ExecutorMessageHandler<SubflowExecutionResult> {
    @Inject
    private ExecutorService executorService;
    @Inject
    private RunContextFactory runContextFactory;
    @Inject
    private MetricRegistry metricRegistry;
    @Inject
    private ExecutionService executionService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private ExecutionStateStore executionStateStore;

    @Inject
    private TaskOutputService taskOutputService;

    @Override
    public Optional<ExecutorContext> handle(SubflowExecutionResult message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        return executionStateStore.lock(message.getParentTaskRun().getExecutionId(), execution ->
        {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getParentTaskRun())) { // TODO if we remove this check, we can avoid adding 'iteration' on the 'isSame()' method
                try {
                    FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                    Task task = flow.findTaskByTaskId(message.getParentTaskRun().getTaskId());
                    TaskRun taskRun;

                    // iterative tasks
                    if (task instanceof ForEachItem.ForEachItemExecutable forEachItem) {
                        // For iterative tasks, we need to get the taskRun from the execution,
                        // move it to the state of the child flow, and merge the outputs.
                        // This is important to avoid races such as RUNNING that arrive after the first SUCCESS/FAILED.
                        RunContext runContext = runContextFactory.of(flow, task, current.getExecution(), message.getParentTaskRun());
                        taskRun = execution.findTaskRunByTaskRunId(message.getParentTaskRun().getId());
                        if (taskRun.getState().getCurrent() != message.getState()) {
                            taskRun = taskRun.withState(message.getState());
                        }
                        Map<String, Object> previousOutputs = taskOutputService.getOutputs(message.getParentTaskRun());
                        Map<String, Object> outputs = MapUtils.deepMerge(previousOutputs, message.getOutputs());
                        var previousTaskRun = execution.findTaskRunByTaskRunId(taskRun.getId());
                        if (previousTaskRun == null) {
                            throw new IllegalStateException("Should never happen");
                        }
                        var taskRunWithOutput = ExecutableUtils.manageIterations(
                            runContext.storage(),
                            taskRun,
                            outputs,
                            previousTaskRun,
                            previousOutputs,
                            forEachItem.getTransmitFailed(),
                            forEachItem.isAllowFailure(),
                            forEachItem.isAllowWarning()
                        );
                        taskRun = taskRunWithOutput.taskRun();
                        taskOutputService.saveOutputs(taskRunWithOutput);
                    } else {
                        taskRun = message.getParentTaskRun();
                        taskOutputService.saveOutputs(taskRun, message.getOutputs());
                    }

                    Execution newExecution = current.getExecution().withTaskRun(taskRun);

                    // If the worker task result is killed, we must check if it has a parents to also kill them if not already done.
                    // Running flowable tasks that have child tasks running in the worker will be killed thanks to that.
                    if (taskRun.getState().getCurrent() == State.Type.KILLED && taskRun.getParentTaskRunId() != null) {
                        newExecution = executionService.killParentTaskruns(taskRun, newExecution);
                    }

                    current = current.withExecution(newExecution, "joinSubflowExecutionResult");

                    // send metrics on parent taskRun terminated
                    if (taskRun.getState().isTerminated()) {
                        metricRegistry
                            .counter(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT, MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(message))
                            .increment();

                        metricRegistry
                            .timer(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION, MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_DURATION_DESCRIPTION, metricRegistry.tags(message))
                            .record(taskRun.getState().getDurationOrComputeIt());

                        log.trace("TaskRun terminated: {}", taskRun);
                    }

                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return executorService.handleFailedExecutionFromExecutor(current, e);
                } catch (FlowNotFoundException e) {
                    // avoid infinite for FlowNotFoundException
                    if (!current.getExecution().getState().getCurrent().isFailed()) {
                        return executorService.handleFailedExecutionFromExecutor(current, e);
                    }
                }
            }

            return null;
        });
    }
}
