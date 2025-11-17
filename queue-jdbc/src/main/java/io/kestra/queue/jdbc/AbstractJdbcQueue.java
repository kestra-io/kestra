package io.kestra.queue.jdbc;

import io.kestra.core.queues.QueueException;
import io.kestra.queue.AbstractQueue;
import io.kestra.queue.GenericEvent;
import io.kestra.queue.QueueUtils;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
public abstract class AbstractJdbcQueue<T extends GenericEvent> extends AbstractQueue<T> {
    protected final JdbcQueueClient jdbcQueueClient;

    public AbstractJdbcQueue(Class<T> cls, QueueUtils queueUtils, JdbcQueueClient jdbcQueueClient) {
        super(cls, queueUtils);
        this.jdbcQueueClient = jdbcQueueClient;
    }

    public void internalEmit(@Nullable String key, T message) throws QueueException {
        String serialize = this.queueUtils.serialize(this.cls, message);

        jdbcQueueClient.publish(this.queueName(), key, message.key(), serialize);
    }

    public void internalEmit(@Nullable String key, List<T> messages) throws QueueException {
        jdbcQueueClient.publish(
            this.queueName(),
            key,
            messages
                .stream()
                .map(throwFunction(e -> {
                    String serialize = this.queueUtils.serialize(this.cls, e);

                    return new AbstractMap.SimpleEntry<>(
                        e.key(),
                        serialize
                    );
                }))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }
}
