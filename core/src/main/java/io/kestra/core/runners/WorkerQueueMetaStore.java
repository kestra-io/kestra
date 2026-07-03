package io.kestra.core.runners;

import java.util.List;
import java.util.Set;

import io.kestra.core.models.tasks.WorkerSelectorMatch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

/**
 * Service interface for accessing Worker Queue routing data from a Kestra Executor.
 *
 * <p>Worker Queues are identified by their user-supplied {@code id} — the routing
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
     * @param tenant       the tenant id, may be {@code null}
     * @param match        the match strategy; {@code null} is treated as {@link WorkerSelectorMatch#ALL}
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
    class DefaultWorkerQueueMetaStore implements WorkerQueueMetaStore {
        @Override
        public boolean hasActiveWorkerForQueue(String id) {
            return true;
        }

        @Override
        public Set<String> listAllWorkerQueueIds() {
            return Set.of();
        }

        @Override
        public List<String> resolveQueueIdsByTags(Set<String> requiredTags, String tenant, WorkerSelectorMatch match) {
            return List.of();
        }
    }
}
