package io.kestra.core.services;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Provides namespace-level variables for pre-execution display expression resolution.
 *
 * <p>This OSS implementation is a no-op that returns an empty map — there are no
 * namespace-level variables in the open-source edition. Enterprise Edition replaces this
 * with a real implementation backed by the namespace meta-store, exposing variables
 * inherited from parent namespaces and making them available in the topology "Show details"
 * view before any execution exists.
 *
 * <p>Flow-level variables take precedence over namespace variables.
 */
@Singleton
public class NamespaceVariablesProvider {

    /**
     * Returns namespace-level variables for the given tenant and namespace.
     *
     * @param tenantId  the tenant identifier, or {@code null} for the main tenant
     * @param namespace the fully-qualified namespace (e.g. {@code io.kestra.prod})
     * @return a map of variable name → value; empty in the OSS edition
     */
    public Map<String, Object> fetchVariables(@Nullable String tenantId, String namespace) {
        return Map.of();
    }
}
