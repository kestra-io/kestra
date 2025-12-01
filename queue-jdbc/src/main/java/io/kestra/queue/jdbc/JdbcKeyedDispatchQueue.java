package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.KeyedDispatchEvent;
import io.kestra.queue.KeyedDispatchQueueInterface;
import io.kestra.queue.QueueSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcKeyedDispatchQueue<T extends KeyedDispatchEvent> extends AbstractJdbcQueue<T> implements KeyedDispatchQueueInterface<T> {
    public JdbcKeyedDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient JdbcQueueClient) {
        super(cls, queueService, JdbcQueueClient);
    }

    @Override
    public void emit(String routingKey, T message) throws QueueException {
        this.internalEmit(routingKey, message);
    }

    @Override
    public void emit(String routingKey, List<T> messages) throws QueueException {
        this.internalEmit(routingKey, messages);
    }

    @Override
    public QueueSubscriber<T> subscriber(String routingKey) throws QueueException {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            List.of(routingKey)
        );
    }
}
