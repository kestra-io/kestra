package io.kestra.runner.mysql;

import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.jdbc.runner.AbstractJdbcQueueFactory;
import io.micronaut.context.annotation.Factory;

@Factory
@MysqlQueueEnabled
public class MysqlQueueFactory extends AbstractJdbcQueueFactory {
    @Override
    protected <T> QueueInterface<T> queue(Class<T> clazz) {
        return new MysqlQueue<>(clazz, applicationContext);
    }

    @Override
    protected WorkerJobQueueInterface workerJobQueue() {
        return new MysqlWorkerJobQueue(applicationContext);
    }

    @Override
    protected QueueInterface<WorkerTriggerResult> workerTriggerResultQueue() {
        return new MysqlWorkerTriggerResultQueue(applicationContext);
    }
}
