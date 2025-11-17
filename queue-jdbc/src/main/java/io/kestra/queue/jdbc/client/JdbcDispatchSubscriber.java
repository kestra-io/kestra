package io.kestra.queue.jdbc.client;

import io.kestra.queue.GenericEvent;
import io.kestra.queue.QueueUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcDispatchSubscriber<T extends GenericEvent> extends JdbcSubscriber<T> {
    private final String routingKey;

    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueUtils queueUtils,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        String routingKey
    ) {
        super(cls, queueUtils, jdbcQueueClient, queueName);

        this.routingKey = routingKey;
    }

    @Override
    protected Integer pool(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer) {
        return this.jdbcQueueClient.subscribeDispatch(this.queueName, this.routingKey, messageConsumer);
    }

    @Override
    protected void init() {
        this.markReady();
    }
}
