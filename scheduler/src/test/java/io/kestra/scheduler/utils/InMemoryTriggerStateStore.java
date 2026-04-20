package io.kestra.scheduler.utils;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;

/**
 * In-memory implementation of {@link TriggerStateStore}.
 * <p>
 * This class MUST only be used for testing purpose.
 */
public class InMemoryTriggerStateStore implements TriggerStateStore {

    // Map from TriggerId to TriggerState
    private final Map<TriggerId, TriggerState> store = new ConcurrentHashMap<>();

    @Override
    public List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) {
        if (vNodes == null || vNodes.isEmpty()) {
            return Collections.emptyList();
        }

        return store.values().stream()
            .filter(ts -> ts.getNextEvaluationDate() != null && !ts.getNextEvaluationDate().isAfter(now.toInstant()))
            .filter(ts -> vNodes.contains(ts.getVnode()))
            .filter(ts -> ts.isLocked() == locked)
            .collect(Collectors.toList());
    }

    @Override
    public List<TriggerState> findAllForVNodes(Set<Integer> vNodes) {
        if (vNodes == null || vNodes.isEmpty()) {
            return Collections.emptyList();
        }

        return store.values().stream()
            .filter(ts -> vNodes.contains(ts.getVnode()))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<TriggerState> findById(TriggerId triggerId) {
        return Optional.ofNullable(store.get(TriggerId.of(triggerId)));
    }

    @Override
    public TriggerState save(TriggerState triggerState) {
        Objects.requireNonNull(triggerState, "triggerState must not be null");
        Objects.requireNonNull(triggerState.getTriggerId(), "triggerState must have a triggerId");
        store.put(TriggerId.of(triggerState), triggerState);
        return triggerState;
    }

    @Override
    public void delete(TriggerId triggerId) {
        store.remove(TriggerId.of(triggerId));
    }

    @Override
    public void init(Set<Integer> vNodes) {
        // In-memory implementation does not need preloading or vnode revocation logic.
        // This could be extended to clear or filter by vNodes if needed.
    }

    /**
     * Clears all data (for testing or reset purposes).
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns the number of stored trigger states.
     */
    public int size() {
        return store.size();
    }
}
