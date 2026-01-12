package io.kestra.queue;

import com.google.common.base.CaseFormat;
import io.kestra.core.queues.GenericQueueInterface;
import io.kestra.core.queues.event.Event;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public abstract class AbstractQueue<T extends Event> implements GenericQueueInterface<T> {
    protected final Class<T> cls;
    protected final QueueService queueService;

    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public AbstractQueue(Class<T> cls, QueueService queueService) {
        this.cls = cls;
        this.queueService = queueService;
    }

    @Override
    public synchronized void addListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    protected String queueNameSeparator() {
        return "__";
    }

    protected String queueName() {
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
        if (routingKey == null) {
            return this.queueName();
        }

        return this.queueName() +
            this.queueNameSeparator() +
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, routingKey);
    }

    protected List<Consumer<T>> listeners() {
        return listeners;
    }
}
