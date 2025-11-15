package io.kestra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

@Slf4j
@Singleton
public class QueueUtils {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false).copy();

    @Inject
    @Named(QueueFactory.QUEUE_EXECUTOR)
    @Getter
    ExecutorService executorService;

    @Inject
    protected QueueConfiguration queueConfiguration;

    @Inject
    private MetricRegistry metricRegistry;

    public void execute(Runnable runnable) {
        this.executorService.execute(runnable);
    }

    public <T extends GenericEvent> String serialize(Class<T> cls, T message) throws QueueException {
        try {
            String serialize = MAPPER.writeValueAsString(message);
            int byteLength = serialize.getBytes(StandardCharsets.UTF_8).length;

            if (queueConfiguration.messageProtection() != null && queueConfiguration.messageProtection().enabled() && byteLength >= queueConfiguration.messageProtection().limit()) {
                metricRegistry
                    .counter(MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT, MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT_DESCRIPTION, MetricRegistry.TAG_CLASS_NAME, cls.getSimpleName()).increment();


                // we let terminated execution messages to go through anyway
                if (!(message instanceof Execution execution) || !execution.getState().isTerminated()) {
                    throw new MessageTooBigException("Message of size " + byteLength + " has exceeded the configured limit of " + queueConfiguration.messageProtection().limit());
                }
            }

            return serialize;
        } catch (JsonProcessingException e) {
            throw new QueueException("Failed to produce '" + message.getClass() + "'", e);
        }
    }

    public <T extends GenericEvent> Either<T, DeserializationException> deserialize(Class<T> cls, byte[] record) {
        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, Arrays.toString(record)));
        }
    }

    public <T extends GenericEvent> Either<T, DeserializationException> deserialize(Class<T> cls, String record) {
        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, record));
        }
    }
}
