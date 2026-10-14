package io.kestra.queue.jdbc.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.GenericQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.queue.jdbc.JdbcQueueEnabled;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@JdbcQueueEnabled
@RequiresMetrics
@Requires(property = "kestra.metrics.query.enabled", value = "true")
@Slf4j
public class JdbcQueueBacklogMetrics {
    private final JdbcQueueClient queueClient;
    private final List<String> queueNames;
    private final AtomicReference<Map<String, Long>> counts = new AtomicReference<>();

    public JdbcQueueBacklogMetrics(JdbcQueueClient queueClient, List<GenericQueueInterface<?>> queues, MetricRegistry metricRegistry) {
        this.queueClient = queueClient;
        // Broadcast rows remain after consumption and do not represent a persistent backlog.
        this.queueNames = queues.stream()
            .filter(queue -> queue instanceof DispatchQueueInterface<?> || queue instanceof KeyedDispatchQueueInterface<?> || queue instanceof VNodeDispatchQueueInterface<?>)
            .map(GenericQueueInterface::queueName)
            .distinct()
            .toList();
        queueNames.forEach(
            queueName -> metricRegistry.gauge(
                MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING,
                MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING_DESCRIPTION,
                (Supplier<Double>) () ->
                {
                    var snapshot = counts.get();
                    return snapshot == null ? Double.NaN : snapshot.getOrDefault(queueName, 0L).doubleValue();
                },
                MetricRegistry.TAG_QUEUE_NAME, queueName
            )
        );
    }

    @Scheduled(initialDelay = "${kestra.metrics.query.interval:1m}", fixedDelay = "${kestra.metrics.query.interval:1m}")
    public void refresh() {
        try {
            counts.set(Map.copyOf(queueClient.pendingMessages(queueNames)));
        } catch (RuntimeException e) {
            log.warn("Unable to refresh JDBC queue backlog metrics.", e);
        }
    }
}
