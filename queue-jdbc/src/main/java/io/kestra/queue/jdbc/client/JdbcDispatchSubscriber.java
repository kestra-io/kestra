package io.kestra.queue.jdbc.client;

import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.queue.QueueService;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcDispatchSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private final List<String> routingKeys;

    /**
     * @deprecated use the overload taking a {@link QueueWakeRegistry} — kept so existing callers
     *             built against the pre-{@link QueueWakeRegistry} signature keep compiling; they
     *             simply get no realtime wake-up.
     */
    @Deprecated
    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        List<String> routingKeys,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        this(cls, queueService, jdbcQueueClient, queueName, routingKeys, metricRegistry, ignoreExecutionService, null);
    }

    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        List<String> routingKeys,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService,
        @Nullable QueueWakeRegistry wakeRegistry) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService, wakeRegistry);

        this.routingKeys = routingKeys;
    }

    @Override
    protected Integer poll(Consumer<byte[]> messageConsumer) {
        return this.jdbcQueueClient.subscribeDispatch(this.queueName, this.routingKeys, messageConsumer);
    }

    @Override
    protected Integer pollBatch(Consumer<List<byte[]>> messageConsumer) {
        return this.jdbcQueueClient.subscribeDispatchBatch(this.queueName, this.routingKeys, messageConsumer);
    }

    @Override
    protected void init() {
        this.markReady();
    }
}
