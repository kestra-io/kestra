package io.kestra.queue.jdbc;

import java.util.List;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.AbstractKeyedDispatchQueue;
import io.kestra.queue.QueueRecord;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcKeyedDispatchQueue<T extends KeyedDispatchEvent> extends AbstractKeyedDispatchQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;
    private final IgnoreExecutionService ignoreExecutionService;

    public JdbcKeyedDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient JdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.jdbcQueueClient = JdbcQueueClient;
        this.metricRegistry = metricRegistry;
        this.ignoreExecutionService = ignoreExecutionService;
    }

    @Override
    public QueueSubscriber<T> subscriber(String routingKey) {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            routingKey == null ? List.of() : List.of(routingKey),
            metricRegistry,
            ignoreExecutionService
        );
    }

    @Override
    public Integer queueLag(String routingKey) {
        return this.jdbcQueueClient.queueLag(queueName(), routingKey);
    }

    @Override
    protected void doEmit(String routingKey, byte[] message, String key) throws QueueException {
        jdbcQueueClient.publish(this.queueName(), routingKey, key, new String(message));
    }

    @Override
    protected void doEmit(String routingKey, List<QueueRecord> messages) throws QueueException {
        String queueName = this.queueName();
        jdbcQueueClient.publish(
            messages
                .stream()
                .map(e -> new JdbcQueueClient.PublishedMessage(queueName, routingKey, e.key(), new String(e.value())))
                .toList()
        );
    }
}
