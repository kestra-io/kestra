package io.kestra.executor;

import java.util.List;
import java.util.Objects;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitResolver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The executor's only entry point to the concurrency scopes an execution claims a slot in: the
 * admission gate, the release fallback and the queued-candidate pop all go through it, so a
 * rule about which scopes an execution counts against is written once.
 */
@Singleton
public class ConcurrencyClaims {
    private final ConcurrencyLimitResolver concurrencyLimitResolver;

    @Inject
    public ConcurrencyClaims(ConcurrencyLimitResolver concurrencyLimitResolver) {
        this.concurrencyLimitResolver = Objects.requireNonNull(concurrencyLimitResolver);
    }

    /**
     * The scopes the execution of this flow claims a slot in, in evaluation order (flow,
     * namespaces innermost first, tenant).
     *
     * @return an empty list when no limit applies
     */
    public List<ScopedConcurrencyLimit> of(FlowInterface flow, Execution execution) {
        return concurrencyLimitResolver.resolveLimits(flow);
    }
}
