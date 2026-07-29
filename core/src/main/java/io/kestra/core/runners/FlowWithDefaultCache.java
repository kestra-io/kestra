package io.kestra.core.runners;

import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.models.flows.FlowWithSource;

import jakarta.inject.Singleton;

/**
 * A cache for flows with plugin defaults already injected, keyed by flow UID (including revision).
 *
 * <p>
 * Cache entries can be selectively expired at tenant or namespace granularity, which is useful
 * when plugin defaults change at those levels.
 * </p>
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
     * Expires all cache entries belonging to the given namespace — or any of its descendants — within
     * a tenant. Namespace-level settings apply hierarchically, so a change on {@code acme} must also
     * re-resolve flows cached under {@code acme.data}.
     *
     * @param tenantId the tenant identifier, may be {@code null} for single-tenant deployments
     * @param namespace the namespace prefix
     */
    public void flush(String tenantId, String namespace) {
        cache.asMap().values().stream()
            .filter(
                flow -> Objects.equals(flow.getTenantId(), tenantId)
                    && (Objects.equals(flow.getNamespace(), namespace) || flow.getNamespace().startsWith(namespace + "."))
            )
            .map(f -> f.uid())
            .forEach(cache::invalidate);
    }

    /**
     * Expires every cache entry across all tenants.
     * Useful when instance-wide settings change.
     */
    public void flushAll() {
        cache.invalidateAll();
    }
}
