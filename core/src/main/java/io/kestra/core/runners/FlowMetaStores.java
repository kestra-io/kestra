package io.kestra.core.runners;

import java.util.Optional;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Helpers over {@link FlowMetaStoreInterface} shared by the routes that create an execution.
 */
@Slf4j
public final class FlowMetaStores {

    private FlowMetaStores() {
    }

    /**
     * Returns the given flow processed for runtime, degrading to the raw flow when it can no longer be
     * resolved, which is the case when it was deleted meanwhile. Callers holding a flow and creating an
     * execution from it use this rather than {@link FlowMetaStoreInterface#findByIdForRuntime} directly, so that
     * a flow vanishing mid-flight behaves the same on every route instead of once per caller.
     */
    public static Flow findForRuntimeOrRaw(FlowMetaStoreInterface flowMetaStore, Flow flow) {
        Optional<FlowWithSource> resolved = flowMetaStore.findByIdForRuntime(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            Optional.ofNullable(flow.getRevision())
        );

        if (resolved.isEmpty()) {
            log.warn("Flow {} cannot be resolved for runtime. Proceeding with the raw flow.", flow.uid());
            return flow;
        }

        return resolved.get();
    }
}
