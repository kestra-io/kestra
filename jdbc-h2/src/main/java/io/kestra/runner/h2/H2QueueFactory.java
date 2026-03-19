package io.kestra.runner.h2;

import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.jdbc.runner.AbstractJdbcQueueFactory;
import io.micronaut.context.annotation.Factory;

@Factory
@H2QueueEnabled
public class H2QueueFactory extends AbstractJdbcQueueFactory {
    @Override
    protected <T> QueueInterface<T> queue(Class<T> clazz) {
        return new H2Queue<>(clazz, applicationContext);
    }

    @Override
    protected WorkerJobQueueInterface workerJobQueue() {
        return new H2WorkerJobQueue(applicationContext);
    }

    @Override
    protected QueueInterface<WorkerTriggerResult> workerTriggerResultQueue() {
        return new H2WorkerTriggerResultQueue(applicationContext);
    }
}
