package io.kestra.queue.jdbc.client;

import io.kestra.queue.GenericEvent;
import io.kestra.queue.QueueUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class JdbcBroadcastSubscriber<T extends GenericEvent> extends JdbcSubscriber<T> {
    public AtomicReference<Long> maxOffset = null;

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueUtils queueUtils,
        JdbcQueueClient jdbcQueueClient,
        String queueName
    ) {
        super(cls, queueUtils, jdbcQueueClient, queueName);
    }

    @Override
    protected Integer pool(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer) {
        return this.jdbcQueueClient.subscribeBroadcast(this.queueName, maxOffset, messageConsumer);
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.subscribeBroadcastMaxOffset(this.queueName);

        this.markReady();
    }
}
