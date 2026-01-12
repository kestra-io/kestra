package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletionStage;

@Slf4j
public class JdbcKeyedDispatchQueue<T extends KeyedDispatchEvent> extends AbstractJdbcQueue<T> implements KeyedDispatchQueueInterface<T> {
    public JdbcKeyedDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient JdbcQueueClient, ExecutorsUtils executorsUtils) {
        super(cls, queueService, JdbcQueueClient, executorsUtils);
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
    public CompletionStage<Void> emitAsync(String routingKey, T message) {
        return this.internalAsyncEmit(routingKey, message);
    }

    @Override
    public CompletionStage<Void> emitAsync(String routingKey, List<T> messages) {
        return this.internalAsyncEmit(routingKey, messages);
    }

    @Override
    public QueueSubscriber<T> subscriber(String routingKey) {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            List.of(routingKey)
        );
    }
}
