package io.kestra.core.services;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class StartExecutorService {
    @Inject
    private ApplicationContext applicationContext;

    private volatile List<String> startExecutors = Collections.emptyList();
    private volatile List<String> notStartExecutors = Collections.emptyList();

    public void applyOptions(List<String> startExecutors, List<String> notStartExecutors) {
        if (!startExecutors.isEmpty() && !notStartExecutors.isEmpty()) {
            throw new IllegalArgumentException("You cannot use both '--start-executors' and '--not-start-executors' options");
        }

        if (!startExecutors.isEmpty() || !notStartExecutors.isEmpty()) {
            String queueType = applicationContext.getProperty("kestra.queue.type", String.class).orElse(null);
            if (queueType != null && !"kafka".equals(queueType)) {
                throw new IllegalArgumentException("Options '--start-executors' and '--not-start-executors' can only be used with kestra.queue.type=kafka");
            }

            this.startExecutors = startExecutors;
            this.notStartExecutors  =notStartExecutors;
        }
    }

    public boolean shouldStartExecutor(String executorName) {
        return !notStartExecutors.contains(executorName) && (startExecutors.isEmpty() || startExecutors.contains(executorName));
    }
}
