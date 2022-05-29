package io.kestra.jdbc.runner;

import io.kestra.core.queues.QueueService;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@Singleton
@Replaces(QueueService.class)
public class JdbcQueueService extends QueueService{
    public String key(Object object) {
        if (object.getClass() == JdbcExecutorState.class) {
            return ((JdbcExecutorState) object).getExecutionId();
        }

        return super.key(object);
    }
}
