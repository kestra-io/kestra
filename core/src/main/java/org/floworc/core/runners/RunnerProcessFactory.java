package org.floworc.core.runners;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.storages.StorageInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Factory
public class RunnerProcessFactory {
    @Prototype
    @Inject
    public Worker worker(
        StorageInterface storageInterface,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        return new Worker(
            storageInterface,
            workerTaskQueue,
            workerTaskResultQueue
        );
    }
}
