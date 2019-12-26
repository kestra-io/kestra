package org.kestra.core.runners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Prototype
@Requires(beans = ExecutionRepositoryInterface.class)
public class Indexer implements Runnable {
    private ExecutionRepositoryInterface executionRepository;
    private QueueInterface<Execution> executionQueue;

    @Inject
    public Indexer(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        ExecutionRepositoryInterface executionRepository
    ) {
        this.executionQueue = executionQueue;
        this.executionRepository = executionRepository;
    }

    @Override
    public void run() {
        executionQueue.receive(Indexer.class, execution -> {
            executionRepository.save(execution);
        });
    }
}
