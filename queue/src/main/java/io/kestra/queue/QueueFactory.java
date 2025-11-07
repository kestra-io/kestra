package io.kestra.queue;

import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;

@Factory
public class QueueFactory {
    public final static String QUEUE_EXECUTOR = "queueExecutor";

    @Named(QUEUE_EXECUTOR)
    @Singleton
    @Inject
    public ExecutorService executorsUtils(ExecutorsUtils executorsUtils, QueueConfiguration queueConfiguration) {
        return executorsUtils.cachedThreadPool("queue-" + queueConfiguration.type());
    }
}
