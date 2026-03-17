package io.kestra.queue.jdbc.client;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Either;
import io.kestra.queue.AbstractPollingSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.poller.QueuePollerConfiguration;

import java.util.List;
import java.util.function.Consumer;

public abstract class JdbcSubscriber<T extends Event> extends AbstractPollingSubscriber<T> {
    protected final JdbcQueueClient jdbcQueueClient;
    protected final String queueName;

    public JdbcSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService
    ) {
        super(cls, queueName, queueService, metricRegistry, ignoreExecutionService, new QueuePollerConfiguration(
            jdbcQueueClient.getConfiguration().minPollInterval(),
            jdbcQueueClient.getConfiguration().maxPollInterval(),
            jdbcQueueClient.getConfiguration().pollSwitchInterval(),
            jdbcQueueClient.getConfiguration().pollSize(),
            jdbcQueueClient.getConfiguration().switchSteps(),
            jdbcQueueClient.getConfiguration().immediateRepoll()
        ));

        this.jdbcQueueClient = jdbcQueueClient;
        this.queueName = queueName;
    }

    protected abstract void init();

    @Override
    public QueueSubscriber<T> subscribe(Consumer<Either<T, DeserializationException>> consumer) {
        this.init();

        return super.subscribe(consumer);
    }

    @Override
    public QueueSubscriber<T> subscribeBatch(Consumer<List<Either<T, DeserializationException>>> consumer) {
        this.init();

        return super.subscribeBatch(consumer);
    }
}
