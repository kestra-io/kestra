package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

/**
 * Builds the per-{@link WorkerStreamContext} {@link WorkerCapacityPolicy} from
 * the worker's initial subscriptions and {@code maxConcurrency}.
 *
 * <p>The {@link Default} factory always returns a
 * {@link SinglePoolCapacityPolicy}. Deployments that need per-queue
 * reservations or capacity borrowing replace this bean with one that returns
 * a richer policy.
 */
public interface WorkerCapacityPolicyFactory {

    /**
     * @param maxConcurrency total slot count the worker advertised
     * @param subscriptions  the worker's resolved {@link QueueSubscription} list
     * @return a fresh policy instance owned by the calling stream context
     */
    WorkerCapacityPolicy create(int maxConcurrency, List<QueueSubscription> subscriptions);

    @Singleton
    @Requires(missingBeans = WorkerCapacityPolicyFactory.class)
    @Secondary
    class Default implements WorkerCapacityPolicyFactory {

        @Override
        public WorkerCapacityPolicy create(int maxConcurrency, List<QueueSubscription> subscriptions) {
            return new SinglePoolCapacityPolicy(maxConcurrency);
        }
    }
}
