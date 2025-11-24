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
import io.kestra.core.utils.ExecutorsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

@Slf4j
@Singleton
public class QueueService {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    @Getter
    protected final ExecutorService executorService;

    protected final QueueConfiguration queueConfiguration;

    private final MetricRegistry metricRegistry;

    @Inject
    public QueueService(ExecutorsUtils executorsUtils, QueueConfiguration queueConfiguration, MetricRegistry metricRegistry) {
        this.executorService = executorsUtils.cachedThreadPool("queue-" + queueConfiguration.getType());
        this.queueConfiguration = queueConfiguration;
        this.metricRegistry = metricRegistry;
    }

    public void execute(Runnable runnable) {
        this.executorService.execute(runnable);
    }

    public <T extends Event> String serialize(Class<T> cls, T message) throws QueueException {
        try {
            String serialize = MAPPER.writeValueAsString(message);

            if (log.isTraceEnabled()) {
                log.trace("[{}] produced message: {}", cls.getSimpleName(), serialize);
            }

            int byteLength = serialize.getBytes(StandardCharsets.UTF_8).length;

            if (queueConfiguration.getMessageProtection() != null && queueConfiguration.getMessageProtection().getEnabled() && byteLength >= queueConfiguration.getMessageProtection().getLimit()) {
                metricRegistry
                    .counter(MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT, MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT_DESCRIPTION, MetricRegistry.TAG_CLASS_NAME, cls.getSimpleName()).increment();

                // we let terminated execution messages to go through anyway
                if (!(message instanceof Execution execution) || !execution.getState().isTerminated()) {
                    throw new MessageTooBigException("Message of size " + byteLength + " has exceeded the configured limit of " + queueConfiguration.getMessageProtection().getLimit());
                }
            }

            return serialize;
        } catch (JsonProcessingException e) {
            throw new QueueException("Failed to produce '" + message.getClass() + "'", e);
        } finally {
            String[] tags = {MetricRegistry.TAG_QUEUE_TYPE, cls.getSimpleName()};
            metricRegistry
                .counter(MetricRegistry.METRIC_QUEUE_PRODUCE_COUNT, MetricRegistry.METRIC_QUEUE_PRODUCE_COUNT_DESCRIPTION, tags)
                .increment();
        }
    }

    public <T extends Event> Either<T, DeserializationException> deserialize(Class<T> cls, byte[] record) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] received message: {}", cls.getSimpleName(), new String(record));
        }

        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, Arrays.toString(record)));
        } finally {
            String[] tags = {MetricRegistry.TAG_QUEUE_TYPE, cls.getSimpleName()};
            metricRegistry
                .counter(MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT, MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT_DESCRIPTION, tags)
                .increment();
        }
    }

    public <T extends Event> Either<T, DeserializationException> deserialize(Class<T> cls, String record) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] received message: {}", cls.getSimpleName(), record);
        }

        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, record));
        } finally {
            String[] tags = {MetricRegistry.TAG_QUEUE_TYPE, cls.getSimpleName()};
            metricRegistry
                .counter(MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT, MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT_DESCRIPTION, tags)
                .increment();
        }
    }
}
