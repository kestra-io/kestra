package io.kestra.queue.jdbc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.AbstractBroadcastQueue;
import io.kestra.queue.QueueRecord;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcBroadcastSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcBroadcastQueue<T extends BroadcastEvent> extends AbstractBroadcastQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;
    private final IgnoreExecutionService ignoreExecutionService;
    private final boolean newTransaction;

    public JdbcBroadcastQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        this(cls, queueService, jdbcQueueClient, executorsUtils, metricRegistry, ignoreExecutionService, true);
    }

    /**
     * @param newTransaction commits each emit on its own connection so that subscribers cannot skip a message committed late by a caller transaction; pass {@code false} for high-volume queues.
     */
    public JdbcBroadcastQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService, boolean newTransaction) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.newTransaction = newTransaction;

        this.jdbcQueueClient = jdbcQueueClient;
        this.metricRegistry = metricRegistry;
        this.ignoreExecutionService = ignoreExecutionService;
    }

    @Override
    protected QueueSubscriber<T> doSubscriber() {
        return new JdbcBroadcastSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            metricRegistry,
            ignoreExecutionService
        );
    }

    @Override
    protected void doEmit(byte[] message, String key) throws QueueException {
        jdbcQueueClient.publish(this.queueName(), null, key, new String(message, StandardCharsets.UTF_8), newTransaction);
    }

    @Override
    protected void doEmit(List<QueueRecord> messages) throws QueueException {
        String queueName = this.queueName();
        jdbcQueueClient.publish(
            messages
                .stream()
                .map(e -> new JdbcQueueClient.PublishedMessage(queueName, null, e.key(), new String(e.value(), StandardCharsets.UTF_8)))
                .toList(),
            newTransaction
        );
    }

}
