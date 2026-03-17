package io.kestra.queue.jdbc.client;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class JdbcDispatchSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private final List<String> routingKeys;

    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        List<String> routingKeys,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService
    ) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService);

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
