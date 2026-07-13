package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;

import jakarta.inject.Singleton;

/**
 * OSS evaluator: no RBAC exists (a single BasicAuth account owns everything), so every tool is
 * allowed. EE replaces this with an evaluator that checks the RBAC mapping declared by its tool
 * subclasses against the principal's grants.
 */
@Singleton
public class DefaultAgentToolPermissionEvaluator implements AgentToolPermissionEvaluator {
    @Override
    public boolean isAllowed(final ToolCatalog.ToolEntry entry, final String tenant, final AgentPrincipal principal) {
        return true;
    }
}
