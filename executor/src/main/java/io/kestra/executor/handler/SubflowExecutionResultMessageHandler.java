package io.kestra.executor.handler;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.*;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.VariablesService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.utils.MapUtils;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.ExecutorMessageHandler;
import io.kestra.plugin.core.flow.ForEachItem;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

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
    private VariablesService variablesService;
    @Inject
    private ExecutionService executionService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private ExecutionStateStore executionStateStore;

    @Override
    public Optional<ExecutorContext> handle(SubflowExecutionResult message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        return executionStateStore.lock(message.getParentTaskRun().getExecutionId(), execution -> {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getParentTaskRun())) { // TODO if we remove this check, we can avoid adding 'iteration' on the 'isSame()' method
                try {
                    FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow();
                    Task task = flow.findTaskByTaskId(message.getParentTaskRun().getTaskId());
                    TaskRun taskRun;

                    // iterative tasks
                    if (task instanceof ForEachItem.ForEachItemExecutable forEachItem) {
                        // For iterative tasks, we need to get the taskRun from the execution,
                        // move it to the state of the child flow, and merge the outputs.
                        // This is important to avoid races such as RUNNING that arrives after the first SUCCESS/FAILED.
                        RunContext runContext = runContextFactory.of(flow, task, current.getExecution(), message.getParentTaskRun());
                        taskRun = execution.findTaskRunByTaskRunId(message.getParentTaskRun().getId());
                        if (taskRun.getState().getCurrent() != message.getState()) {
                            taskRun = taskRun.withState(message.getState());
                        }
                        Map<String, Object> outputs = MapUtils.deepMerge(taskRun.getOutputs(), message.getParentTaskRun().getOutputs());
                        Variables variables = variablesService.of(StorageContext.forTask(taskRun), outputs);
                        taskRun = taskRun.withOutputs(variables);
                        taskRun = ExecutableUtils.manageIterations(
                            runContext.storage(),
                            taskRun,
                            current.getExecution(),
                            forEachItem.getTransmitFailed(),
                            forEachItem.isAllowFailure(),
                            forEachItem.isAllowWarning()
                        );
                    } else {
                        taskRun = message.getParentTaskRun();
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
                            .record(taskRun.getState().getDuration());

                        log.trace("TaskRun terminated: {}", taskRun);
                    }

                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return executorService.handleFailedExecutionFromExecutor(current, e);
                }
            }

            return null;
        });
    }
}
