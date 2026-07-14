package io.kestra.core.runners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.models.tasks.WorkerSelectorMatch;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessListener;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.WorkerQueues;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Service interface for accessing Worker Queue routing data from a Kestra Executor.
 *
 * <p>
 * Worker Queues are identified by their user-supplied {@code id} — the routing
 * identity used to dispatch jobs to queues. The {@code id} is immutable: tags, tenant
 * scope, and metadata may change on a queue, but its {@code id} cannot.
 */
public interface WorkerQueueMetaStore {

    /**
     * Checks whether the Worker Queue currently has an active worker able to serve it.
     * <p>
     * Returns {@code true} when at least one running worker is subscribed to the queue, or when
     * the id is {@code null} or a reserved routing sentinel ({@code default}, {@code system}) that
     * carries an implicit consumer.
     *
     * @param id The Worker Queue's id - can be {@code null}.
     * @return {@code true} if a worker can serve the queue (or it is a null/default/system sentinel), {@code false} otherwise.
     */
    boolean hasActiveWorkerForQueue(String id);

    /**
     * Returns the set of all existing Worker Queue ids.
     */
    Set<String> listAllWorkerQueueIds();

    /**
     * Resolves the Worker Queue ids matching {@code requiredTags} under the given
     * {@code match} strategy, ordered best-first.
     *
     * @param requiredTags the selector tags (case-insensitive)
     * @param tenant the tenant id, may be {@code null}
     * @param match the match strategy; {@code null} is treated as {@link WorkerSelectorMatch#ALL}
     * @return the matching Worker Queue ids ordered best-first; empty when none match
     */
    List<String> resolveQueueIdsByTags(Set<String> requiredTags, String tenant, WorkerSelectorMatch match);

    /**
     * Default {@link WorkerQueueMetaStore} implementation.
     * This class is only used if no other implementation exist.
     */
    @Singleton
    @Requires(missingBeans = WorkerQueueMetaStore.class)
    @Secondary
    class DefaultWorkerQueueMetaStore implements WorkerQueueMetaStore, ServiceLivenessListener {
        private static final String CACHE_KEY = "worker-queue-routing";
        private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(5);

        private final ServiceLivenessStore serviceLivenessStore;
        private final Cache<String, RoutingSnapshot> routingSnapshotCache;

        public DefaultWorkerQueueMetaStore() {
            this(null);
        }

        @Inject
        public DefaultWorkerQueueMetaStore(@Nullable ServiceLivenessStore serviceLivenessStore) {
            this(serviceLivenessStore, DEFAULT_CACHE_TTL);
        }

        DefaultWorkerQueueMetaStore(@Nullable ServiceLivenessStore serviceLivenessStore, Duration cacheTtl) {
            this.serviceLivenessStore = serviceLivenessStore;
            this.routingSnapshotCache = Caffeine.newBuilder()
                .expireAfterWrite(Objects.requireNonNull(cacheTtl, "cacheTtl cannot be null"))
                .build();
        }

        @Override
        public boolean hasActiveWorkerForQueue(String id) {
            if (id == null || WorkerQueues.isDefault(id) || WorkerQueues.SYSTEM_ID.equals(id)) {
                return true;
            }
            if (serviceLivenessStore == null) {
                return true;
            }
            return routingSnapshot().activeWorkerQueueIds().contains(id);
        }

        @Override
        public Set<String> listAllWorkerQueueIds() {
            return routingSnapshot().activeWorkerQueueIds();
        }

        @Override
        public List<String> resolveQueueIdsByTags(Set<String> requiredTags, String tenant, WorkerSelectorMatch match) {
            return List.of();
        }

        @Override
        public void onLivenessUpdate(Instant now, ServiceInstance instance, Service.ServiceState newState) {
            if (instance != null && instance.is(ServiceType.WORKER)) {
                invalidate();
            }
        }

        void invalidate() {
            routingSnapshotCache.invalidate(CACHE_KEY);
        }

        private RoutingSnapshot routingSnapshot() {
            return routingSnapshotCache.get(CACHE_KEY, ignored -> loadRoutingSnapshot());
        }

        private RoutingSnapshot loadRoutingSnapshot() {
            if (serviceLivenessStore == null) {
                return RoutingSnapshot.empty();
            }

            Set<String> activeWorkerQueueIds = serviceLivenessStore
                .findAllInstancesInStates(Service.ServiceState.allRunningStates())
                .stream()
                .filter(instance -> instance.is(ServiceType.WORKER))
                .map(DefaultWorkerQueueMetaStore::workerQueueId)
                .flatMap(Optional::stream)
                .filter(workerQueueId -> !WorkerQueues.isDefault(workerQueueId))
                .collect(Collectors.toUnmodifiableSet());

            return new RoutingSnapshot(activeWorkerQueueIds);
        }

        private static Optional<String> workerQueueId(ServiceInstance instance) {
            return Optional.ofNullable(instance.props())
                .map(props -> props.get(WorkerGroups.SERVICE_PROPS_KEY))
                .map(Object::toString)
                .map(WorkerQueues::normalize);
        }

        private record RoutingSnapshot(Set<String> activeWorkerQueueIds) {
            private static RoutingSnapshot empty() {
                return new RoutingSnapshot(Set.of());
            }
        }
    }
}
