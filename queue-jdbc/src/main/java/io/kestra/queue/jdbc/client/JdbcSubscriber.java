package io.kestra.queue.jdbc.client;

import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Either;
import io.kestra.queue.AbstractPollingSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.poller.QueuePollerConfiguration;
import io.kestra.queue.poller.QueueWaker;

import jakarta.annotation.Nullable;

public abstract class JdbcSubscriber<T extends Event> extends AbstractPollingSubscriber<T> {
    protected final JdbcQueueClient jdbcQueueClient;
    protected final String queueName;

    @Nullable
    private final QueueWakeRegistry wakeRegistry;

    /**
     * @deprecated use {@link #JdbcSubscriber(Class, QueueService, JdbcQueueClient, String, MetricRegistry, IgnoreExecutionService, QueueWakeRegistry)}
     *             — kept so existing callers built against the pre-{@link QueueWakeRegistry} signature keep compiling; they simply get no realtime wake-up.
     */
    @Deprecated
    public JdbcSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        this(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService, null);
    }

    public JdbcSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService,
        @Nullable QueueWakeRegistry wakeRegistry) {
        super(
            cls, queueName, queueService, metricRegistry, ignoreExecutionService, new QueuePollerConfiguration(
                jdbcQueueClient.getConfiguration().minPollInterval(),
                jdbcQueueClient.getConfiguration().maxPollInterval(),
                jdbcQueueClient.getConfiguration().pollSwitchInterval(),
                jdbcQueueClient.getConfiguration().pollSize(),
                jdbcQueueClient.getConfiguration().switchSteps(),
                jdbcQueueClient.getConfiguration().immediateRepoll()
            )
        );

        this.jdbcQueueClient = jdbcQueueClient;
        this.queueName = queueName;
        this.wakeRegistry = wakeRegistry;
    }

    protected abstract void init();

    @Override
    protected QueueWaker waker() {
        return wakeRegistry != null ? wakeRegistry.waker(queueName) : super.waker();
    }

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
