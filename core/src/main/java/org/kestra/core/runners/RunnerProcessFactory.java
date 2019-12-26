package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.storages.StorageInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Factory
public class RunnerProcessFactory {
    @Prototype
    @Inject
    public Worker worker(
        StorageInterface storageInterface,
        ApplicationContext applicationContext,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        return new Worker(
            storageInterface,
            applicationContext,
            workerTaskQueue,
            workerTaskResultQueue
        );
    }
}
