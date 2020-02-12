package org.kestra.core.runners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import org.kestra.core.metrics.MetricRegistry;
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
    private MetricRegistry metricRegistry;

    @Inject
    public Indexer(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        ExecutionRepositoryInterface executionRepository,
        MetricRegistry metricRegistry
    ) {
        this.executionQueue = executionQueue;
        this.executionRepository = executionRepository;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        executionQueue.receive(Indexer.class, execution -> {
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_COUNT).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_DURATION).record(() -> {
                executionRepository.save(execution);
            });
        });
    }
}
