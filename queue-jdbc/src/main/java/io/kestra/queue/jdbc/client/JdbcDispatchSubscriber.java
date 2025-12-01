package io.kestra.queue.jdbc.client;

import io.kestra.queue.Event;
import io.kestra.queue.QueueService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcDispatchSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private final List<String> routingKeys;

    public JdbcDispatchSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        List<String> routingKeys
    ) {
        super(cls, queueService, jdbcQueueClient, queueName);

        this.routingKeys = routingKeys;
    }

    @Override
    protected Integer poll(JdbcQueueClient.MessageConsumer<String, Exception> messageConsumer) {
        return this.jdbcQueueClient.subscribeDispatch(this.queueName, this.routingKeys, messageConsumer);
    }

    @Override
    protected void init() {
        this.markReady();
    }
}
