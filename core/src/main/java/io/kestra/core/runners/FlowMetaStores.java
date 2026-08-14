package io.kestra.core.runners;

import java.util.Optional;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.FlowParsingService;

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
     * <p>
     * The pinned label keys come with it: a route building an execution before it emits its {@code Create}
     * command needs them, since the executor drops those keys from what the command contributes, and a route
     * that kept them would render inputs, and report labels, against a value the execution will not carry.
     */
    public static ProcessedFlow findForRuntimeOrRaw(FlowMetaStoreInterface flowMetaStore, Flow flow) {
        Optional<ProcessedFlow> resolved = flowMetaStore.findByIdForRuntime(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            Optional.ofNullable(flow.getRevision())
        );

        if (resolved.isEmpty()) {
            log.warn("Flow {} cannot be resolved for runtime. Proceeding with the raw flow.", flow.uid());
            return ProcessedFlow.of(FlowParsingService.toFlowWithSource(flow));
        }

        return resolved.get();
    }
}
