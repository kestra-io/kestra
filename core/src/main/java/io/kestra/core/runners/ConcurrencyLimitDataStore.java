package io.kestra.core.runners;

public interface ConcurrencyLimitDataStore {
    /**
     * Update a concurrency limit
     * WARNING: this is inherently unsafe and must only be used for administration
     */
    ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit);

    /**
     * Delete a concurrency limit.
     * WARNING: this is inherently unsafe and must only be used for administration
     */
    ConcurrencyLimit delete(ConcurrencyLimit concurrencyLimit);
}
