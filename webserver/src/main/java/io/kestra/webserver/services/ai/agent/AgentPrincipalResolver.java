package io.kestra.webserver.services.ai.agent;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

public interface AgentPrincipalResolver {
    @Nullable
    AgentPrincipal resolve();

    /**
     * Resolves a caller outside an HTTP request, from the identifier the request authenticated them as.
     *
     * @param callerId the caller, or {@code null} when anonymous
     * @param tenant the tenant the caller acts in
     * @return the caller acting in that tenant, or {@code null} when no identity applies
     */
    @Nullable
    AgentPrincipal resolve(@Nullable String callerId, String tenant);

    /**
     * @param callerId the caller, or {@code null} when anonymous
     * @return the tenants the caller may act in
     */
    List<String> reachableTenants(@Nullable String callerId);
}
