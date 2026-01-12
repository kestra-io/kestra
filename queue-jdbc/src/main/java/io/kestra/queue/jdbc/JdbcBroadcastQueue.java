package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcBroadcastSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletionStage;

@Slf4j
public class JdbcBroadcastQueue<T extends BroadcastEvent> extends AbstractJdbcQueue<T> implements BroadcastQueueInterface<T> {
    public JdbcBroadcastQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils) {
        super(cls, queueService, jdbcQueueClient, executorsUtils);
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
    public CompletionStage<Void> emitAsync(T message) {
        return this.internalAsyncEmit(null, message);
    }

    @Override
    public CompletionStage<Void> emitAsync(List<T> messages) {
        return this.internalAsyncEmit(null, messages);
    }

    @Override
    public QueueSubscriber<T> subscriber() {
        return new JdbcBroadcastSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName()
        );
    }
}
