package io.kestra.queue.jdbc.client;

import io.kestra.queue.Event;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class JdbcBroadcastSubscriber<T extends Event> extends JdbcSubscriber<T> {
    public Long maxOffset = null;

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName
    ) {
        super(cls, queueService, jdbcQueueClient, queueName);
    }

    @Override
    protected Integer poll(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer) {
        Pair<Integer, Long> result = this.jdbcQueueClient.subscribeBroadcast(this.queueName, maxOffset, messageConsumer);
        maxOffset = result.getRight();

        return result.getLeft();
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.fetchMaxOffset(this.queueName);

        this.markReady();
    }
}
