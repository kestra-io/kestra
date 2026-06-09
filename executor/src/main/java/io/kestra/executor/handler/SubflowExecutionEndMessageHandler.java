package io.kestra.executor.handler;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.executor.*;

import jakarta.inject.Inject;
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
    private DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue;

    @Inject
    private KillSwitchService killSwitchService;

    @Override
    public void handle(SubflowExecutionEnd message) {
        if (killSwitchService.evaluate(message.childExecution()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution end for child execution {} as there is a kill switch in it", message.childExecution().getId());
            return;
        }
        if (killSwitchService.evaluate(message.parentExecutionId()) != EvaluationType.PASS) {
            log.warn("Ignoring subflow execution end for parent execution {} as there is a kill switch in it", message.parentExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        executionStateStore.lock(message.parentExecutionId(), execution ->
        {
            try {
                FlowWithSource flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                ExecutableTask<?> executableTask = (ExecutableTask<?>) flow.findTaskByTaskId(message.taskId());
                if (!executableTask.waitForExecution()) {
                    return null;
                }

                TaskRun taskRun = execution.findTaskRunByTaskRunId(message.taskRunId()).withState(message.state());
                FlowInterface childFlow = flowMetaStore.findByExecution(message.childExecution()).orElseThrow();
                RunContext runContext = runContextFactory.of(
                    childFlow,
                    (Task) executableTask,
                    message.childExecution(),
                    taskRun
                );

                SubflowExecutionResult subflowExecutionResult = ExecutableUtils
                    .subflowExecutionResultFromChildExecution(runContext, childFlow, message.childExecution(), executableTask, taskRun);
                if (subflowExecutionResult != null) {
                    try {
                        this.subflowExecutionResultQueue.emit(subflowExecutionResult);
                    } catch (QueueException ex) {
                        log.error("Unable to emit the subflow execution result", ex);
                    }
                }
            } catch (InternalException | FlowNotFoundException e) {
                log.error("Unable to process the subflow execution end", e);
            }
            return null;
        });
    }
}
