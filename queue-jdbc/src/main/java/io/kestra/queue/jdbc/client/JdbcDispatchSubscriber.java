package io.kestra.queue.jdbc.client;

import io.kestra.queue.Event;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcDispatchSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private final String routingKey;

    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        String routingKey
    ) {
        super(cls, queueService, jdbcQueueClient, queueName);

        this.routingKey = routingKey;
    }

    @Override
    protected Integer poll(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer) {
        return this.jdbcQueueClient.subscribeDispatch(this.queueName, this.routingKey, messageConsumer);
    }

    @Override
    protected void init() {
        this.markReady();
    }
}
