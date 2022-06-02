package io.kestra.jdbc.runner;

import io.kestra.core.queues.QueueService;
import io.kestra.core.runners.ExecutionDelay;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Singleton
@Replaces(QueueService.class)
public class JdbcQueueService extends QueueService{
    public String key(Object object) {
        if (object.getClass() == JdbcExecutorState.class) {
            return ((JdbcExecutorState) object).getExecutionId();
        } else if (object.getClass() == ExecutionDelay.class) {
            return ((ExecutionDelay) object).getExecutionId();
        }

        return super.key(object);
    }
}
