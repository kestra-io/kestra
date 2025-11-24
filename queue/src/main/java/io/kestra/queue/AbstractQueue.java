package io.kestra.queue;

import com.google.common.base.CaseFormat;
import jakarta.annotation.Nullable;

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
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this.cls.getSimpleName());
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
