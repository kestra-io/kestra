package io.kestra.executor.handler;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.executor.*;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SubflowExecutionEndMessageHandler implements MessageHandler<SubflowExecutionEnd> {
    @Inject
    private ExecutorService executorService;

    @Inject
    private ExecutionStateStore executionStateStore;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    private QueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;

    @Override
    public void handle(SubflowExecutionEnd message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        executionStateStore.lock(message.getParentExecutionId(), execution -> {
            FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow();
            try {
                ExecutableTask<?> executableTask = (ExecutableTask<?>) flow.findTaskByTaskId(message.getTaskId());
                if (!executableTask.waitForExecution()) {
                    return null;
                }

                TaskRun taskRun = execution.findTaskRunByTaskRunId(message.getTaskRunId()).withState(message.getState()).withOutputs(message.getOutputs());
                FlowInterface childFlow = flowMetaStore.findByExecution(message.getChildExecution()).orElseThrow();
                RunContext runContext = runContextFactory.of(
                    childFlow,
                    (Task) executableTask,
                    message.getChildExecution(),
                    taskRun
                );

                SubflowExecutionResult subflowExecutionResult = ExecutableUtils.subflowExecutionResultFromChildExecution(runContext, childFlow, message.getChildExecution(), executableTask, taskRun);
                if (subflowExecutionResult != null) {
                    try {
                        this.subflowExecutionResultQueue.emit(subflowExecutionResult);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the subflow execution result", ex);
                    }
                }
            } catch (InternalException e) {
                log.error("Unable to process the subflow execution end", e);
            }
            return null;
        });
    }
}
