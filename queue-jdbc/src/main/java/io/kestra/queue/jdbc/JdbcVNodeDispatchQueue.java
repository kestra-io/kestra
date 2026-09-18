package io.kestra.queue.jdbc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.kestra.queue.jdbc.client.QueueWakeRegistry;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcVNodeDispatchQueue<T extends VNodeDispatchEvent> extends AbstractVNodeDispatchQueue<T> {
    private final JdbcQueueClient jdbcQueueClient;
    private final MetricRegistry metricRegistry;
    private final IgnoreExecutionService ignoreExecutionService;

    @Nullable
    private final QueueWakeRegistry queueWakeRegistry;

    /**
     * @deprecated use the overload taking a {@link QueueWakeRegistry} — kept so existing callers
     *             built against the pre-{@link QueueWakeRegistry} signature keep compiling; they
     *             simply get no realtime wake-up.
     */
    @Deprecated
    public JdbcVNodeDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        this(cls, queueService, jdbcQueueClient, executorsUtils, metricRegistry, ignoreExecutionService, null);
    }

    public JdbcVNodeDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService, @Nullable QueueWakeRegistry queueWakeRegistry) {
        super(cls, queueService, executorsUtils, metricRegistry);

        this.jdbcQueueClient = jdbcQueueClient;
        this.metricRegistry = metricRegistry;
        this.ignoreExecutionService = ignoreExecutionService;
        this.queueWakeRegistry = queueWakeRegistry;
    }

    @Override
    protected void doEmit(byte[] message, String key) throws QueueException {
        jdbcQueueClient.publish(
            this.queueName(),
            this.vNodeRoutingKey(this.queueService.computeVNode(key)),
            key,
            new String(message, StandardCharsets.UTF_8)
        );
    }

    @Override
    protected void doEmit(List<QueueRecord> messages) throws QueueException {
        String queueName = this.queueName();
        jdbcQueueClient.publish(
            messages
                .stream()
                .map(
                    e -> new JdbcQueueClient.PublishedMessage(
                        queueName,
                        this.vNodeRoutingKey(this.queueService.computeVNode(e.key())),
                        e.key(),
                        new String(e.value(), StandardCharsets.UTF_8)
                    )
                )
                .toList()
        );
    }

    @Override
    protected QueueSubscriber<T> doSubscriber(Set<Integer> vNodes) {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            vNodes
                .stream()
                .map(this::vNodeRoutingKey)
                .toList(),
            metricRegistry,
            ignoreExecutionService,
            queueWakeRegistry
        );
    }
}
