package io.kestra.webserver.services.ai.agent;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.tenant.TenantService;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default resolver: no multi-user identity exists (a single account), so there is no principal to
 * capture, and every caller may act in every tenant. A replacement can override this to resolve the
 * authenticated user.
 */
@Singleton
public class DefaultAgentPrincipalResolver implements AgentPrincipalResolver {
    private final TenantService tenantService;

    @Inject
    public DefaultAgentPrincipalResolver(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public AgentPrincipal resolve() {
        return null;
    }

    @Override
    public AgentPrincipal resolve(@Nullable String callerId, String tenant) {
        return null;
    }

    @Override
    public List<String> reachableTenants(@Nullable String callerId) {
        return tenantService.listTenants();
    }
}
