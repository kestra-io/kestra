package io.kestra.queue;

import com.google.common.base.CaseFormat;
import jakarta.annotation.Nullable;

public abstract class AbstractQueue<T extends GenericEvent> {
    protected final Class<T> cls;
    protected final QueueUtils queueUtils;

    public AbstractQueue(Class<T> cls, QueueUtils queueUtils) {
        this.cls = cls;
        this.queueUtils = queueUtils;
    }

    protected String queueNameSeparator() {
        return "__";
    }

    protected String queueName() {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this.cls.getSimpleName());
    }

    protected String queueName(@Nullable String key) {
        if (key == null) {
            return this.queueName();
        }

        return this.queueName() +
            this.queueNameSeparator() +
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, key);
    }
}
