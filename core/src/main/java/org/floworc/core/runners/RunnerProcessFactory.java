package org.floworc.core.runners;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Factory
public class RunnerProcessFactory {
    @Prototype
    @Inject
    public Worker worker(
        @Named("executionQueue") QueueInterface<Execution> executionQueue,
        @Named("workerTaskQueue") QueueInterface<WorkerTask> workerTaskQueue,
        @Named("workerTaskResultQueue") QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        return new Worker(
            workerTaskQueue,
            workerTaskResultQueue
        );
    }

    @Prototype
    public Executor executor(
        @Named("executionQueue") QueueInterface<Execution> executionQueue,
        @Named("workerTaskQueue") QueueInterface<WorkerTask> workerTaskQueue,
        @Named("workerTaskResultQueue") QueueInterface<WorkerTaskResult> workerTaskResultQueue,
        FlowRepositoryInterface flowRepository,
        ExecutionService executionService
    ) {
        return new Executor(
            executionQueue,
            workerTaskQueue,
            flowRepository,
            executionService
        );
    }

    @Prototype
    public ExecutionService executionService(
        @Named("workerTaskResultQueue") QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        return new ExecutionService(workerTaskResultQueue);
    }
}
