package io.kestra.queue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.GenericQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.ExecutorsUtils;

import io.micrometer.core.instrument.Counter;
import jakarta.annotation.Nullable;

abstract class AbstractQueue<T extends Event> implements GenericQueueInterface<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractQueue.class);

    protected final Class<T> cls;
    protected final QueueService queueService;
    protected final ExecutorService asyncPoolExecutor;
    protected final Counter emitCounter;
    protected final MetricRegistry metricRegistry;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final List<QueueSubscriber<?>> subscribers = new CopyOnWriteArrayList<>();

    AbstractQueue(Class<T> cls, QueueService queueService, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry) {
        this.cls = cls;
        this.queueService = queueService;
        this.metricRegistry = metricRegistry;
        int maxAsyncThreads = Math.max(4, executorsUtils.getAllocatedCpuCores());
        this.asyncPoolExecutor = executorsUtils.maxCachedVirtualThreadPool(maxAsyncThreads, "queue-async-" + queueName());
        this.emitCounter = metricRegistry.counter(MetricRegistry.METRIC_QUEUE_MESSAGE_EMITTED_TOTAL, MetricRegistry.METRIC_QUEUE_MESSAGE_EMITTED_TOTAL_DESCRIPTION, MetricRegistry.TAG_QUEUE_NAME, queueName());
        metricRegistry.gauge(MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_ACTIVE, MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_ACTIVE_DESCRIPTION, (Supplier<Integer>) subscribers::size, MetricRegistry.TAG_QUEUE_NAME, queueName());

        if (LOG.isDebugEnabled()) {
            this.listeners.add(message -> LOG.debug("[{}] emitted message with key: {}", cls.getSimpleName(), message.key()));
        }
    }

    @Override
    public synchronized void addListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    protected String queueNameSeparator() {
        return "__";
    }

    @Override
    public String queueName() {
        String result = "";

        if (queueService.getQueueConfiguration().getPrefix() != null) {
            result = queueService.getQueueConfiguration().getPrefix() + this.queueNameSeparator();
        }

        return result + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this.cls.getSimpleName());
    }

    protected String vNodeRoutingKey(Integer vNode) {
        return "vnode_" + vNode;
    }

    protected List<String> queuesName(Set<Integer> vNodes) {
        return vNodes
            .stream()
            .map(this::vNodeRoutingKey)
            .map(this::queueName)
            .toList();
    }

    protected String queueName(@Nullable String routingKey) {
        if (routingKey == null || routingKey.isEmpty()) {
            return this.queueName();
        }

        return this.queueName() +
            this.queueNameSeparator() +
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, routingKey);
    }

    protected List<Consumer<T>> listeners() {
        return listeners;
    }

    /**
     * Wraps a subscriber in a {@link MonitoredQueueSubscriber} for pause/resume metric tracking
     * and registers it so it can be closed when the queue is closed. The returned wrapper also
     * calls {@link #untrackSubscriber(QueueSubscriber)} on close so {@code queue.subscribers.count}
     * reflects only currently-live subscribers.
     *
     * @param subscriber the underlying subscriber to track
     * @return the monitoring wrapper, for fluent chaining
     */
    protected QueueSubscriber<T> trackSubscriber(QueueSubscriber<T> subscriber) {
        MonitoredQueueSubscriber<T> wrapper = new MonitoredQueueSubscriber<>(subscriber, this, metricRegistry);
        subscribers.add(wrapper);
        return wrapper;
    }

    /**
     * Removes a subscriber from the tracking list. Called by {@link MonitoredQueueSubscriber#close()}.
     */
    void untrackSubscriber(QueueSubscriber<?> subscriber) {
        subscribers.remove(subscriber);
    }

    @Override
    public void close() {
        for (QueueSubscriber<?> subscriber : subscribers) {
            if (subscriber.isActive()) {
                LOG.warn("{} closing subscriber that was not closed by its caller", queueName());
                try {
                    subscriber.close();
                } catch (Exception e) {
                    LOG.error("{} error closing subscriber", queueName(), e);
                }
            }
        }
        subscribers.clear();
        this.asyncPoolExecutor.shutdown();
    }
}
