package io.kestra.core.runners;

import java.util.Set;

import io.kestra.core.models.flows.FlowWithSource;

/**
 * A flow processed for runtime, together with what governance decided about it.
 *
 * @param flow the flow with plugin defaults injected and, on editions supporting it, governance applied
 * @param pinnedLabelKeys the label keys an overriding policy rule force-set, which whoever creates an
 *        execution must not be able to supply a value for
 */
public record ProcessedFlow(FlowWithSource flow, Set<String> pinnedLabelKeys) {

    public ProcessedFlow {
        pinnedLabelKeys = pinnedLabelKeys == null ? Set.of() : Set.copyOf(pinnedLabelKeys);
    }

    /** A flow governance pinned nothing on, which is every flow in an edition without policies. */
    public static ProcessedFlow of(FlowWithSource flow) {
        return new ProcessedFlow(flow, Set.of());
    }
}
