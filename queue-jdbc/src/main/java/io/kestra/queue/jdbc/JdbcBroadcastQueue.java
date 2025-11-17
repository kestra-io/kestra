package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.BroadcastEvent;
import io.kestra.queue.BroadcastQueueInterface;
import io.kestra.queue.QueueSubscriber;
import io.kestra.queue.QueueUtils;
import io.kestra.queue.jdbc.client.JdbcBroadcastSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcBroadcastQueue<T extends BroadcastEvent> extends AbstractJdbcQueue<T> implements BroadcastQueueInterface<T> {
    public JdbcBroadcastQueue(Class<T> cls, QueueUtils queueUtils, JdbcQueueClient jdbcQueueClient) {
        super(cls, queueUtils, jdbcQueueClient);
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
        return new JdbcBroadcastSubscriber<>(
            cls,
            queueUtils,
            jdbcQueueClient,
            queueName()
        );
    }
}
