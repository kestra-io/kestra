package org.kestra.core.runners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.LogRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Prototype
@Requires(beans = {ExecutionRepositoryInterface.class, LogRepositoryInterface.class})
public class Indexer implements IndexerInterface {
    private ExecutionRepositoryInterface executionRepository;
    private LogRepositoryInterface logRepository;
    private QueueInterface<Execution> executionQueue;
    private QueueInterface<LogEntry> logQueue;
    private MetricRegistry metricRegistry;

    @Inject
    public Indexer(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        ExecutionRepositoryInterface executionRepository,
        LogRepositoryInterface logRepository,
        MetricRegistry metricRegistry
    ) {
        this.executionQueue = executionQueue;
        this.logQueue = logQueue;
        this.executionRepository = executionRepository;
        this.logRepository = logRepository;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        executionQueue.receive(Indexer.class, execution -> {
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT).increment();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION).record(() -> {
                executionRepository.save(execution);
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT).increment();
            });
        });

        logQueue.receive(Indexer.class, logEntry -> {
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT).increment();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION).record(() -> {
                logRepository.save(logEntry);
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT).increment();
            });
        });
    }
}
