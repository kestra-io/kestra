package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public class JdbcVNodeDispatchQueue<T extends VNodeDispatchEvent> extends AbstractVNodeDispatchQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;

    public JdbcVNodeDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.jdbcQueueClient = jdbcQueueClient;
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected void doEmit(byte[] message, String key) throws QueueException {
        jdbcQueueClient.publish(
            this.queueName(),
            this.vNodeRoutingKey(this.queueService.computeVNode(key)),
            key,
            new String(message));
    }

    @Override
    protected void doEmit(List<QueueRecord> messages) throws QueueException {
        String queueName = this.queueName();
        jdbcQueueClient.publish(messages
            .stream()
            .map(e -> new JdbcQueueClient.PublishedMessage(
                queueName,
                this.vNodeRoutingKey(this.queueService.computeVNode(e.key())),
                e.key(),
                new String(e.value())
            ))
            .toList()
        );
    }

    @Override
    public QueueSubscriber<T> subscriber(Set<Integer> vNodes) {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            vNodes
                .stream()
                .map(this::vNodeRoutingKey)
                .toList(),
            metricRegistry
        );
    }
}
