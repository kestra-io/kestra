package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.client.JdbcDispatchSubscriber;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public class JdbcVNodeDispatchQueue<T extends VNodeDispatchEvent> extends AbstractJdbcQueue<T> implements VNodeDispatchQueueInterface<T> {
    public JdbcVNodeDispatchQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils) {
        super(cls, queueService, jdbcQueueClient, executorsUtils);
    }

    @Override
    public void emit(T message) throws QueueException {
        this.emit(Collections.singletonList(message));
    }

    @Override
    public void emit(List<T> messages) throws QueueException {
        jdbcQueueClient.publish(messages
            .stream()
            .map(throwFunction(e -> {
                byte[] serialize = this.queueService.serialize(this.cls, e);

                return new JdbcQueueClient.PublishedMessage(
                    this.queueName(),
                    this.vNodeRoutingKey(this.queueService.computeVNode(e.key())),
                    e.key(),
                    new String(serialize)
                );
            }))
            .toList()
        );

        listeners().forEach(l -> messages.forEach(l));
    }

    @Override
    public CompletionStage<Void> emitAsync(T message) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.emit(message);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, this.asyncPoolExecutor);
    }

    @Override
    public CompletionStage<Void> emitAsync(List<T> messages) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.emit(messages);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, this.asyncPoolExecutor);
    }

    @Override
    public QueueSubscriber<T> subscriber(Set<Integer> vNodes) {
        return new JdbcDispatchSubscriber<>(
            cls,
            queueService,
            jdbcQueueClient,
            queueName(),
            vNodes
                .stream()
                .map(this::vNodeRoutingKey)
                .toList()
        );
    }
}
