package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.AbstractQueue;
import io.kestra.queue.Event;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractJdbcQueue<T extends Event> extends AbstractQueue<T> {
    protected final JdbcQueueClient jdbcQueueClient;

    public AbstractJdbcQueue(Class<T> cls, QueueService queueService, JdbcQueueClient jdbcQueueClient) {
        super(cls, queueService);
        this.jdbcQueueClient = jdbcQueueClient;
    }

    protected void internalEmit(@Nullable String routingKey, T message) throws QueueException {
        String serialize = this.queueService.serialize(this.cls, message);

        jdbcQueueClient.publish(this.queueName(), routingKey, message.key(), serialize);
    }

    protected void internalEmit(@Nullable String routingKey, List<T> messages) throws QueueException {
        jdbcQueueClient.publish(messages
            .stream()
            .map(throwFunction(e -> {
                String serialize = this.queueService.serialize(this.cls, e);

                return new JdbcQueueClient.PublishedMessage(this.queueName(), routingKey, e.key(), serialize);
            }))
            .toList()
        );
    }
}
