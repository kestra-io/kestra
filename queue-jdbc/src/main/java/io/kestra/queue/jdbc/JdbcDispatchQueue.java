package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.DispatchEvent;
import io.kestra.queue.DispatchQueueInterface;
import io.kestra.queue.QueueSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcDispatchQueue<T extends DispatchEvent> extends AbstractJdbcQueue<T> implements DispatchQueueInterface<T> {
    public JdbcDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient) {
        super(cls, queueService, jdbcQueueClient);
    }

    @Override
    public void emit(T message) throws QueueException {
        this.internalEmit(null, message);
    }

    @Override
    public void emit(List<T> messages) throws QueueException {
        this.internalEmit(null, messages);
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            null
        );
    }
}
