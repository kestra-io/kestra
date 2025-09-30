package io.kestra.scheduler.stores;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.scheduler.model.TriggerState;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TriggerStateStore {
    
    /**
     * Finds all {@link Trigger} instances that are eligible to be scheduled as of the specified timestamp.
     *
     * @param now
     *        the current timestamp used to evaluate scheduling eligibility;
     *        triggers with a next execution time less than or equal to this
     *        value are considered eligible
     * @param vNodes
     *        the set of virtual node identifiers used to restrict the search scope;
     * @param locked
     *        if {@code true}, only locked triggers are returned;
     *        if {@code false}, only unlocked triggers are returned
     * @return a list of triggers that are eligible for scheduling at the given time
     */
    List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked);
    
    /**
     * Returns all {@link TriggerState} instances belonging to the given vNodes.
     *
     * @param vNodes the virtual node identifiers
     * @return the list of corresponding {@link TriggerState}s
     */
    List<TriggerState> findAllForVNodes(Set<Integer> vNodes);
    
    /**
     * Finds a {@link TriggerState} for a given identifier.
     *
     * @param triggerId The trigger identifier.
     * @return  an optional {@link TriggerState}
     */
    Optional<TriggerState> find(TriggerId triggerId);
    
    /**
     * Saves the given {@link TriggerState}.
     *
     * @param triggerState The trigger state.
     */
    void save(TriggerState triggerState);
    
    /**
     * Deletes the state for the specified identifier.
     *
     * @param triggerId The trigger identifier.
     */
    void delete(TriggerId triggerId);
    
    /**
     * Initialize this state store for the given virtual nodes.
     * <p>
     * This method can be used to preload internal cache for the given vNodes. Removes revoked vnodes,
     * and loads triggers for newly assigned vNodes.
     * 
     * @param vNodes the set of vnodes.
     */
    default void init(final Set<Integer> vNodes) {
        /* noop */
    }
}
