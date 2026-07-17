package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import jakarta.inject.Singleton;

/**
 * Default evaluator: no authorization model exists (a single account owns everything), so every tool
 * is allowed. A replacement can override this to check the permission declared by each tool against
 * the principal.
 */
@Singleton
public class DefaultAgentToolPermissionEvaluator implements AgentToolPermissionEvaluator {
    @Override
    public boolean isAllowed(final ToolCatalog.ToolEntry entry, final String tenant, final AgentPrincipal principal) {
        return true;
    }
}
