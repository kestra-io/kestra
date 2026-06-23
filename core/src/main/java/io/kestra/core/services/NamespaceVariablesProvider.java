package io.kestra.core.services;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Provides namespace-level variables for display expression resolution in the topology graph.
 *
 * <p>The OSS implementation returns an empty map. Enterprise Edition overrides this bean
 * (via {@code @Replaces}) to fetch variables from the namespace variable store, allowing
 * {@code {{ vars.* }}} expressions defined at the namespace level to be resolved in the
 * topology "Show details" modal and sidebar without requiring a live execution.
 *
 * <p>Flow-level variables (defined in the flow's {@code variables:} block) always take
 * precedence over namespace-level variables — the merge happens in {@link GraphService}.
 */
@Singleton
public class NamespaceVariablesProvider {

    /**
     * Returns the namespace-level variables for the given namespace.
     *
     * @param tenantId  the tenant identifier, or {@code null} for single-tenant setups
     * @param namespace the namespace identifier
     * @return a map of variable name to value; never {@code null}
     */
    public Map<String, Object> fetchVariables(@Nullable String tenantId, String namespace) {
        return Map.of();
    }
}
