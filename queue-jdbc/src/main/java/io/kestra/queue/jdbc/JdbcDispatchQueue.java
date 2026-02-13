package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.AbstractDispatchQueue;
import io.kestra.queue.QueueRecord;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcDispatchQueue<T extends DispatchEvent> extends AbstractDispatchQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;

    public JdbcDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.jdbcQueueClient = jdbcQueueClient;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            null,
            metricRegistry
        );
    }

    @Override
    protected void doEmit(byte[] message, String key) throws QueueException {
        jdbcQueueClient.publish(this.queueName(), null, key, new String(message));
    }

    @Override
    protected void doEmit(List<QueueRecord> messages) throws QueueException {
        String queueName = this.queueName();
        jdbcQueueClient.publish(messages
            .stream()
            .map(e -> new JdbcQueueClient.PublishedMessage(queueName, null, e.key(), new String(e.value())))
            .toList()
        );
    }

}
