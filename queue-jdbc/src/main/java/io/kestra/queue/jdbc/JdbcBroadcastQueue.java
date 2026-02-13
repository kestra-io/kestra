package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.AbstractBroadcastQueue;
import io.kestra.queue.QueueRecord;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcBroadcastSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcBroadcastQueue<T extends BroadcastEvent> extends AbstractBroadcastQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;

    public JdbcBroadcastQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.jdbcQueueClient = jdbcQueueClient;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new JdbcBroadcastSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
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
