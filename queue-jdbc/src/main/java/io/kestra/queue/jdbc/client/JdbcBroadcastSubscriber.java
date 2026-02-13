package io.kestra.queue.jdbc.client;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.event.Event;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class JdbcBroadcastSubscriber<T extends Event> extends JdbcSubscriber<T> {
    public Long maxOffset = null;

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry
    ) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry);
    }

    @Override
    protected Integer poll(Consumer<String> messageConsumer) {
        Pair<Integer, Long> result = this.jdbcQueueClient.subscribeBroadcast(this.queueName, maxOffset, messageConsumer);
        maxOffset = result.getRight();

        return result.getLeft();
    }

    @Override
    protected Integer pollBatch(Consumer<List<String>> messageConsumer) {
        Pair<Integer, Long> result = this.jdbcQueueClient.subscribeBroadcastBatch(this.queueName, maxOffset, messageConsumer);
        maxOffset = result.getRight();

        return result.getLeft();
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.fetchMaxOffset(this.queueName);

        this.markReady();
    }
}
