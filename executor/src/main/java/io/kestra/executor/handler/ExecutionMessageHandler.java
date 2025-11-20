package io.kestra.executor.handler;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Singleton
@Slf4j
public class ExecutionMessageHandler implements ExecutorMessageHandler<Execution> {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_EVENT_NAMED)
    private QueueInterface<ExecutionEvent> executionEventQueue;

    @Inject
    private ExecutionStateStore executionStateStore;

    @Override
    public Optional<ExecutorContext> handle(Execution message) {
        try {
            executionEventQueue.emit(new ExecutionEvent(message, ExecutionEventType.CREATED));
            return Optional.empty();
        } catch (QueueException e) {
            // If we cannot send the execution event, we fail the execution
            return executionStateStore.lock(message.getId(), execution -> {
                try {
                    Execution failed = execution.failedExecutionFromExecutor(e).getExecution().withState(State.Type.FAILED);
                    ExecutionEvent event = new ExecutionEvent(failed, ExecutionEventType.TERMINATED);
                    this.executionEventQueue.emit(event);
                    return new ExecutorContext(failed);
                } catch (QueueException ex) {
                    log.error("Unable to emit the execution {}", execution.getId(), ex);
                }
                return null;
            });
        }
    }
}
