package io.kestra.queue;

import com.google.common.base.CaseFormat;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;

public abstract class AbstractQueue<T extends Event> {
    protected final Class<T> cls;
    protected final QueueService queueService;

    public AbstractQueue(Class<T> cls, QueueService queueService) {
        this.cls = cls;
        this.queueService = queueService;
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
}
