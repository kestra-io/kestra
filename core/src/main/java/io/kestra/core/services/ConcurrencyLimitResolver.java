package io.kestra.core.services;

import java.util.List;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ScopedConcurrencyLimit;

import jakarta.inject.Singleton;

/**
 * Resolves the concurrency limits applying to the executions of a flow.
 * <p>
 * Limits are returned in evaluation order — flow, namespace, parent namespaces (innermost
 * first), tenant — and the first limit reached defines the behavior. An execution that runs
 * claims one slot in <b>every</b> returned scope.
 * <p>
 * Namespace and tenant level limits are an EE feature: this OSS implementation only resolves
 * the flow's own {@code concurrency} property.
 */
@Singleton
public class ConcurrencyLimitResolver {
    /**
     * All concurrency limits applying to executions of the given flow, in evaluation order.
     *
     * @return an empty list when no limit applies
     */
    public List<ScopedConcurrencyLimit> resolveLimits(FlowInterface flow) {
        return flow.getConcurrency() == null ? List.of() : List.of(ScopedConcurrencyLimit.ofFlow(flow));
    }
}
