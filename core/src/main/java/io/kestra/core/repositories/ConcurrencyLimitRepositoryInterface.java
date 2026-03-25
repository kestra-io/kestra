package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.runners.ConcurrencyLimit;

import jakarta.validation.constraints.NotNull;

public interface ConcurrencyLimitRepositoryInterface {
    /**
     * Update a concurrency limit
     * WARNING: this is inherently unsafe and must only be used for administration
     */
    ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit);

    /**
     * Returns all concurrency limits from the database for a given tenant
     */
    List<ConcurrencyLimit> find(String tenantId);

    /**
     * Find a concurrency limit by its id
     */
    Optional<ConcurrencyLimit> findById(@NotNull String tenantId, @NotNull String namespace, @NotNull String flowId);
}
