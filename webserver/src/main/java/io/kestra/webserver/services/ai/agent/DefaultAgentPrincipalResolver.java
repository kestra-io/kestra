package io.kestra.webserver.services.ai.agent;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import jakarta.inject.Singleton;

/**
 * Default resolver: no multi-user identity exists (a single account), so there is no principal to
 * capture. A replacement can override this to resolve the authenticated user.
 */
@Singleton
public class DefaultAgentPrincipalResolver implements AgentPrincipalResolver {
    @Override
    public AgentPrincipal resolve() {
        return null;
    }
}
