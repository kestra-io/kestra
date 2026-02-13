package io.kestra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.Event;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

@Slf4j
@Singleton
public class QueueService {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    private final MetricRegistry metricRegistry;

    @Getter
    private final int vNodeCount;

    @Getter
    protected final ExecutorService executorService;

    @Getter
    protected final QueueConfiguration queueConfiguration;

    @Inject
    public QueueService(ExecutorsUtils executorsUtils, QueueConfiguration queueConfiguration, MetricRegistry metricRegistry, SchedulerConfiguration schedulerConfiguration) {
        this.executorService = executorsUtils.cachedThreadPool("queue-" + queueConfiguration.getType());
        this.queueConfiguration = queueConfiguration;
        this.metricRegistry = metricRegistry;
        this.vNodeCount = schedulerConfiguration.vnodes();
    }

    public void execute(Runnable runnable) {
        this.executorService.execute(runnable);
    }

    public int computeVNode(String key) {
        return VNodes.computeVNode(this.vNodeCount, key);
    }

    public <T extends Event> byte[] serialize(Class<T> cls, T message) throws QueueException {
        try {
            byte[] serialize = MAPPER.writeValueAsBytes(message);

            if (queueConfiguration.getMessageProtection() != null && queueConfiguration.getMessageProtection().getEnabled() && serialize.length >= queueConfiguration.getMessageProtection().getLimit()) {
                metricRegistry
                    .counter(MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT, MetricRegistry.METRIC_QUEUE_BIG_MESSAGE_COUNT_DESCRIPTION, MetricRegistry.TAG_CLASS_NAME, cls.getSimpleName()).increment();

                // we let terminated execution messages to go through anyway
                if (!(message instanceof Execution execution) || !execution.getState().isTerminated()) {
                    throw new MessageTooBigException("[" + cls.getSimpleName() + "] message of size " + serialize.length + " has exceeded the configured limit of " + queueConfiguration.getMessageProtection().getLimit());
                }
            }

            return serialize;
        } catch (JsonProcessingException e) {
            throw new QueueException("[" + cls.getSimpleName() + "] failed to produce: " + e.getMessage(), e);
        }
    }

    public <T extends Event> Either<T, DeserializationException> deserialize(Class<T> cls, byte[] record) {
        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, Arrays.toString(record)));
        } finally {
            String[] tags = {MetricRegistry.TAG_QUEUE_NAME, cls.getSimpleName()};
            metricRegistry
                .counter(MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT, MetricRegistry.METRIC_QUEUE_RECEIVE_COUNT_DESCRIPTION, tags)
                .increment();
        }
    }
}
