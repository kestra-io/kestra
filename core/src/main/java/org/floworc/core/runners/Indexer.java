package org.floworc.core.runners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.ExecutionRepositoryInterface;

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
