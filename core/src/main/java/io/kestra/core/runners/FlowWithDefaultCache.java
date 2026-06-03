package io.kestra.core.runners;

import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.flows.FlowWithSource;

import jakarta.inject.Singleton;

/**
 * A cache for flows with plugin defaults already injected, keyed by flow UID (including revision).
 *
 * <p>Cache entries can be selectively expired at tenant or namespace granularity, which is useful
 * when plugin defaults change at those levels.</p>
 */
@Singleton
public class FlowWithDefaultCache {
    private final Cache<String, FlowWithSource> cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .recordStats()
        .build();

    /** Returns the cached entry for the given flow UID, or empty if absent. */
    public Optional<FlowWithSource> getIfPresent(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    /** Adds or replaces a cache entry. */
    public void put(String key, FlowWithSource flow) {
        cache.put(key, flow);
    }

    /** Expires the cache entry for the given flow UID. */
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    /**
     * Expires all cache entries belonging to the given tenant.
     * Useful when tenant-level plugin defaults change.
     *
     * @param tenantId the tenant identifier, may be {@code null} for single-tenant deployments
     */
    public void flush(String tenantId) {
        cache.asMap().values().stream()
            .filter(flow -> Objects.equals(flow.getTenantId(), tenantId))
            .map(f -> f.uid())
            .forEach(cache::invalidate);
    }

    /**
     * Expires all cache entries belonging to the given namespace within a tenant.
     * Useful when namespace-level plugin defaults change.
     *
     * @param tenantId  the tenant identifier, may be {@code null} for single-tenant deployments
     * @param namespace the namespace
     */
    public void flush(String tenantId, String namespace) {
        cache.asMap().values().stream()
            .filter(flow -> Objects.equals(flow.getTenantId(), tenantId) && Objects.equals(flow.getNamespace(), namespace))
            .map(f -> f.uid())
            .forEach(cache::invalidate);
    }

    @VisibleForTesting
    void clear() {
        cache.invalidateAll();
    }
}
