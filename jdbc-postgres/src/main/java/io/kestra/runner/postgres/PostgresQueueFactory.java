package io.kestra.runner.postgres;

import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.jdbc.runner.AbstractJdbcQueueFactory;
import io.micronaut.context.annotation.Factory;

@Factory
@PostgresQueueEnabled
public class PostgresQueueFactory extends AbstractJdbcQueueFactory {
    @Override
    protected <T> QueueInterface<T> queue(Class<T> clazz) {
        return new PostgresQueue<>(clazz, applicationContext);
    }

    @Override
    protected WorkerJobQueueInterface workerJobQueue() {
        return new PostgresWorkerJobQueue(applicationContext);
    }

    @Override
    protected QueueInterface<WorkerTriggerResult> workerTriggerResultQueue() {
        return new PostgresWorkerTriggerResultQueue(applicationContext);
    }
}
