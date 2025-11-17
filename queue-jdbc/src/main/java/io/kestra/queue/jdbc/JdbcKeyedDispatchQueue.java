package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.KeyedDispatchEvent;
import io.kestra.queue.KeyedDispatchQueueInterface;
import io.kestra.queue.QueueSubscriber;
import io.kestra.queue.QueueUtils;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcKeyedDispatchQueue<T extends KeyedDispatchEvent> extends AbstractJdbcQueue<T> implements KeyedDispatchQueueInterface<T> {
    public JdbcKeyedDispatchQueue(Class<T> cls, QueueUtils queueUtils, JdbcQueueClient JdbcQueueClient) {
        super(cls, queueUtils, JdbcQueueClient);
    }

    @Override
    public void emit(String key, T message) throws QueueException {
        this.internalEmit(key, message);
    }

    @Override
    public void emit(String key, List<T> messages) throws QueueException {
        this.internalEmit(key, messages);
    }

    @Override
    public QueueSubscriber<T> subscriber(String key) throws QueueException {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueUtils,
            jdbcQueueClient,
            queueName(),
            key
        );
    }
}
