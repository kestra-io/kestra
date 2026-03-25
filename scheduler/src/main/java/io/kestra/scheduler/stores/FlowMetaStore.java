package io.kestra.scheduler.stores;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.scheduler.model.TriggerState;

/**
 * Service interface providing a read-only access on flows.
 */
public interface FlowMetaStore {

    /**
     * Finds a {@link FlowWithSource} for a given identifier.
     *
     * @param flowId The flow identifier.
     * @return an optional {@link TriggerState}
     */
    Optional<FlowWithSource> find(FlowId flowId);

    /**
     * Returns all {@link Flow} instances belonging to the given vNodes.
     *
     * @param vNodes the virtual node identifiers
     * @return the list of corresponding {@link Flow}s
     */
    List<FlowWithSource> findAllForVNodes(final Set<Integer> vNodes);

    /**
     * Initialize this state store for the given virtual nodes.
     * <p>
     * This method can be used to preload internal cache for the given vNodes. Removes revoked vnodes,
     * and loads flows for newly assigned vNodes.
     *
     * @param vNodes the set of vnodes.
     */
    default void init(final Set<Integer> vNodes) {
        /* noop */
    }
}
