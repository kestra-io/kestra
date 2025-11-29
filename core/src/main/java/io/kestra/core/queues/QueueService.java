package io.kestra.core.queues;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.*;
import jakarta.inject.Singleton;

@Singleton
public class QueueService {
    public String key(Object object) {
        if (object instanceof HasUID hasUID) {
            return hasUID.uid();
        } else if (object.getClass() == LogEntry.class) {
            return null;
        } else if (object.getClass() == MetricEntry.class) {
            return null;
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }
}
