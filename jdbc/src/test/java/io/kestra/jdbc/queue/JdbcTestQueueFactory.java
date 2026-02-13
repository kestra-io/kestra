package io.kestra.jdbc.queue;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.JdbcBroadcastQueue;
import io.kestra.queue.jdbc.JdbcDispatchQueue;
import io.kestra.queue.jdbc.JdbcKeyedDispatchQueue;
import io.kestra.queue.jdbc.JdbcVNodeDispatchQueue;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;

@Factory
public class JdbcTestQueueFactory {
    @Inject
    private QueueService queueService;

    @Inject
    private JdbcQueueClient jdbcQueueClient;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private MetricRegistry metricRegistry;

    @Bean
    public BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> broadCastQueue() {
        return new JdbcBroadcastQueue<>(AbstractBroadcastQueueTest.TestBroadcast.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean
    public DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue() {
        return new JdbcDispatchQueue<>(AbstractDispatchQueueTest.TestDispatch.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean
    public KeyedDispatchQueueInterface<AbstractKeyedDispatchQueueTest.TestKeyedDispatch> keyDispatchQueue() {
        return new JdbcKeyedDispatchQueue<>(AbstractKeyedDispatchQueueTest.TestKeyedDispatch.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean
    public VNodeDispatchQueueInterface<AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch> vNodeDispatchQueue() {
        return new JdbcVNodeDispatchQueue<>(AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean
    public BroadcastQueueInterface<AbstractQueueCacheTest.DeletableBroadcastTestEvent> deletableBroadcastTestEventDispatchQueue() {
        return new JdbcBroadcastQueue<>(AbstractQueueCacheTest.DeletableBroadcastTestEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }
}
