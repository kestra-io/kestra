package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.AbstractQueue;
import io.kestra.core.queues.event.Event;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractJdbcQueue<T extends Event> extends AbstractQueue<T> {
    private static final int MAX_ASYNC_THREADS = Runtime.getRuntime().availableProcessors();

    protected final JdbcQueueClient jdbcQueueClient;
    protected final ExecutorService asyncPoolExecutor;

    public AbstractJdbcQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient, ExecutorsUtils executorsUtils) {
        super(cls, queueService);
        this.jdbcQueueClient = jdbcQueueClient;
        this.asyncPoolExecutor = executorsUtils.maxCachedThreadPool(MAX_ASYNC_THREADS, "jdbc-queue-async-" + cls.getSimpleName());
    }

    protected void internalEmit(@Nullable String routingKey, T message) throws QueueException {
        byte[] serialize = this.queueService.serialize(this.cls, message);

        jdbcQueueClient.publish(this.queueName(), routingKey, message.key(), new String(serialize));

        listeners().forEach(l -> l.accept(message));
    }

    protected void internalEmit(@Nullable String routingKey, List<T> messages) throws QueueException {
        jdbcQueueClient.publish(messages
            .stream()
            .map(throwFunction(e -> {
                byte[] serialize = this.queueService.serialize(this.cls, e);

                return new JdbcQueueClient.PublishedMessage(this.queueName(), routingKey, e.key(), new String(serialize));
            }))
            .toList()
        );

        listeners().forEach(l -> messages.forEach(l));
    }

    protected CompletionStage<Void> internalAsyncEmit(@Nullable String routingKey, T message) {
        return CompletableFuture.runAsync(() -> {
            try {
                internalEmit(routingKey, message);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, asyncPoolExecutor);
    }

    protected CompletionStage<Void> internalAsyncEmit(@Nullable String routingKey, List<T> messages) {
        return CompletableFuture.runAsync(() -> {
            try {
                internalEmit(routingKey, messages);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, asyncPoolExecutor);
    }
}
