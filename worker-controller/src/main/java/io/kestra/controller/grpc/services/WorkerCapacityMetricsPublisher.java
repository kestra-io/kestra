package io.kestra.controller.grpc.services;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.WorkerQueues;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes per-(worker group, worker queue) capacity gauges from the controller's live
 * {@link WorkerJobDispatcher} state, so the webserver can serve a live capacity snapshot
 * without a separate cross-process RPC.
 *
 * <p>Lifecycle-driven: this class implements {@link WorkerLifecycleListener} and is
 * wired into the dispatcher by Micronaut DI as a constructor argument. The dispatcher
 * invokes {@link #init(WorkerJobDispatcher)} at the end of its own constructor, which
 * captures the reference used by gauge suppliers. Gauges are added on the first observed
 * {@code (groupId, queueId)} (or {@code groupId}) and removed via
 * {@link MetricRegistry#removeMeter} when the last contributor disappears, so cardinality
 * tracks the <em>currently active</em> fleet rather than the historical one.
 *
 * <p>The five metrics — see {@link MetricRegistry#METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED}
 * and siblings — are pull-based: each {@link Supplier} reads live state from
 * {@link WorkerJobDispatcher#activeStreams()} at scrape time.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class WorkerCapacityMetricsPublisher implements WorkerLifecycleListener {

    private final MetricRegistry metricRegistry;

    /**
     * Set once via {@link #init(WorkerJobDispatcher)} at dispatcher construction time
     * and read on every gauge scrape. Volatile to publish the reference safely across
     * the (dispatcher constructor thread → gRPC handler thread) boundary; the actual
     * cross-thread synchronization on first read happens through the dispatcher's own
     * publication, but the volatile costs nothing and keeps intent explicit.
     */
    private volatile WorkerJobDispatcher dispatcher;

    /**
     * Refcount of active workers contributing to a {@code (groupId, queueId)} pair.
     * Entry exists iff the corresponding subscription gauges are registered.
     */
    private final ConcurrentHashMap<SubKey, Integer> subscriptionRefCounts = new ConcurrentHashMap<>();

    /**
     * Refcount of active workers in a {@code groupId}. Entry exists iff the group-scoped
     * gauges (shared allocated/used, group inflight) are registered.
     */
    private final ConcurrentHashMap<String, Integer> groupRefCounts = new ConcurrentHashMap<>();

    @Inject
    public WorkerCapacityMetricsPublisher(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void init(WorkerJobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void onWorkerRegistered(WorkerStreamContext<?> context) {
        String groupId = context.getWorkerGroupId();
        for (String queueId : context.subscribedWorkerQueueIds()) {
            incrementSubscription(groupId, queueId);
        }
        incrementGroup(groupId);
    }

    @Override
    public void onWorkerUnregistered(WorkerStreamContext<?> context) {
        String groupId = context.getWorkerGroupId();
        for (String queueId : context.subscribedWorkerQueueIds()) {
            decrementSubscription(groupId, queueId);
        }
        decrementGroup(groupId);
    }

    @Override
    public void onWorkerSubscriptionsChanged(WorkerStreamContext<?> context, Set<String> added, Set<String> removed) {
        String groupId = context.getWorkerGroupId();
        for (String queueId : removed) {
            decrementSubscription(groupId, queueId);
        }
        for (String queueId : added) {
            incrementSubscription(groupId, queueId);
        }
    }

    private void incrementSubscription(String groupId, String queueId) {
        SubKey key = new SubKey(groupId, queueId);
        subscriptionRefCounts.compute(key, (k, prev) -> {
            int next = (prev == null ? 0 : prev) + 1;
            if (prev == null) {
                registerSubscriptionGauges(groupId, queueId);
            }
            return next;
        });
    }

    private void decrementSubscription(String groupId, String queueId) {
        SubKey key = new SubKey(groupId, queueId);
        subscriptionRefCounts.compute(key, (k, prev) -> {
            if (prev == null) {
                // Defensive: should not happen if register/unregister are balanced.
                log.warn("Subscription refcount underflow for group='{}' queue='{}'", groupId, queueId);
                return null;
            }
            int next = prev - 1;
            if (next <= 0) {
                removeSubscriptionGauges(groupId, queueId);
                return null;
            }
            return next;
        });
    }

    private void incrementGroup(String groupId) {
        groupRefCounts.compute(groupId, (k, prev) -> {
            int next = (prev == null ? 0 : prev) + 1;
            if (prev == null) {
                registerGroupGauges(groupId);
            }
            return next;
        });
    }

    private void decrementGroup(String groupId) {
        groupRefCounts.compute(groupId, (k, prev) -> {
            if (prev == null) {
                log.warn("Group refcount underflow for group='{}'", groupId);
                return null;
            }
            int next = prev - 1;
            if (next <= 0) {
                removeGroupGauges(groupId);
                return null;
            }
            return next;
        });
    }

    private void registerSubscriptionGauges(String groupId, String queueId) {
        String[] tags = metricRegistry.workerGroupAndQueueTags(groupId, queueId);
        metricRegistry.gauge(
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED,
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED_DESCRIPTION,
            (Supplier<Integer>) () -> sumAllocatedFor(groupId, queueId),
            tags
        );
        metricRegistry.gauge(
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_USED,
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_USED_DESCRIPTION,
            (Supplier<Integer>) () -> sumUsedFor(groupId, queueId),
            tags
        );
    }

    private void registerGroupGauges(String groupId) {
        String[] tags = metricRegistry.workerGroupTags(groupId);
        metricRegistry.gauge(
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED,
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED_DESCRIPTION,
            (Supplier<Integer>) () -> sumSharedAllocatedFor(groupId),
            tags
        );
        metricRegistry.gauge(
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_USED,
            MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_USED_DESCRIPTION,
            (Supplier<Integer>) () -> sumSharedUsedFor(groupId),
            tags
        );
        metricRegistry.gauge(
            MetricRegistry.METRIC_CONTROLLER_WORKER_GROUP_JOB_INFLIGHT,
            MetricRegistry.METRIC_CONTROLLER_WORKER_GROUP_JOB_INFLIGHT_DESCRIPTION,
            (Supplier<Integer>) () -> sumInflightFor(groupId),
            tags
        );
    }

    private void removeSubscriptionGauges(String groupId, String queueId) {
        removeGaugesByTags(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, groupId, queueId);
        removeGaugesByTags(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_USED, groupId, queueId);
    }

    private void removeGroupGauges(String groupId) {
        removeGaugesByGroupTag(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED, groupId);
        removeGaugesByGroupTag(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_USED, groupId);
        removeGaugesByGroupTag(MetricRegistry.METRIC_CONTROLLER_WORKER_GROUP_JOB_INFLIGHT, groupId);
    }

    private void removeGaugesByTags(String metricName, String groupId, String queueId) {
        metricRegistry.find(metricName)
            .tag(MetricRegistry.TAG_WORKER_GROUP, WorkerGroups.normalize(groupId))
            .tag(MetricRegistry.TAG_WORKER_QUEUE, WorkerQueues.normalize(queueId))
            .gauges()
            .forEach(metricRegistry::removeMeter);
    }

    private void removeGaugesByGroupTag(String metricName, String groupId) {
        metricRegistry.find(metricName)
            .tag(MetricRegistry.TAG_WORKER_GROUP, WorkerGroups.normalize(groupId))
            .gauges()
            .forEach(metricRegistry::removeMeter);
    }

    private int sumAllocatedFor(String groupId, String queueId) {
        int sum = 0;
        for (WorkerStreamContext<?> ctx : dispatcher.activeStreams()) {
            if (!groupId.equals(ctx.getWorkerGroupId())) {
                continue;
            }
            if (ctx.subscribedWorkerQueueIds().contains(queueId)) {
                sum += ctx.guaranteedCapacity(queueId);
            }
        }
        return sum;
    }

    private int sumUsedFor(String groupId, String queueId) {
        int sum = 0;
        for (WorkerStreamContext<?> ctx : dispatcher.activeStreams()) {
            if (!groupId.equals(ctx.getWorkerGroupId())) {
                continue;
            }
            if (ctx.subscribedWorkerQueueIds().contains(queueId)) {
                sum += ctx.guaranteedUsed(queueId);
            }
        }
        return sum;
    }

    private int sumSharedAllocatedFor(String groupId) {
        int sum = 0;
        for (WorkerStreamContext<?> ctx : dispatcher.activeStreams()) {
            if (groupId.equals(ctx.getWorkerGroupId())) {
                sum += ctx.sharedCapacity();
            }
        }
        return sum;
    }

    private int sumSharedUsedFor(String groupId) {
        int sum = 0;
        for (WorkerStreamContext<?> ctx : dispatcher.activeStreams()) {
            if (groupId.equals(ctx.getWorkerGroupId())) {
                sum += ctx.sharedUsed();
            }
        }
        return sum;
    }

    private int sumInflightFor(String groupId) {
        int sum = 0;
        for (WorkerStreamContext<?> ctx : dispatcher.activeStreams()) {
            if (groupId.equals(ctx.getWorkerGroupId())) {
                sum += ctx.getInFlightCount();
            }
        }
        return sum;
    }

    record SubKey(String workerGroupId, String workerQueueId) {
    }
}
