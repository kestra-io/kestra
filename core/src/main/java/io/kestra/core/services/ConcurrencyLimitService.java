package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.ConcurrencyLimit;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Contains methods to manage concurrency limit.
 * This is designed to be used by the API, the executor use lower level primitives.
 */
public interface ConcurrencyLimitService {

    Set<State.Type> VALID_TARGET_STATES =
        EnumSet.of(State.Type.RUNNING, State.Type.CANCELLED, State.Type.FAILED);

    /**
     * Unqueue a queued execution.
     *
     * @throws IllegalArgumentException in case the execution is not queued.
     */
    Execution unqueue(Execution execution, State.Type state) throws QueueException;

    /**
     * Find concurrency limits.
     */
    List<ConcurrencyLimit> find(String tenantId);

    /**
     * Update a concurrency limit.
     */
    ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit);

    /**
     * Find a concurrency limit by its identifier.
     */
    Optional<ConcurrencyLimit> findById(String tenantId, String namespace, String flowId);
}
