package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

/**
 * Resolves worker group subscriptions from a worker group ID.
 * The default (OSS) implementation returns the default subscription;
 * EE implementations resolve from a persisted Worker Group.
 */
public interface WorkerQueueResolver {

    /**
     * @param workerGroupId the worker group ID (empty for OSS / no worker group)
     * @return the resolved subscriptions; never null or empty
     */
    List<QueueSubscription> resolve(String workerGroupId);

    /**
     * Default (OSS) implementation used when no other {@link WorkerQueueResolver} bean is
     * registered. Always returns the default queue subscription; EE supplies a worker-group-aware
     * resolver.
     */
    @Singleton
    @Requires(missingBeans = WorkerQueueResolver.class)
    @Secondary
    class Default implements WorkerQueueResolver {

        @Override
        public List<QueueSubscription> resolve(String workerGroupId) {
            return List.of(QueueSubscription.DEFAULT);
        }
    }
}
