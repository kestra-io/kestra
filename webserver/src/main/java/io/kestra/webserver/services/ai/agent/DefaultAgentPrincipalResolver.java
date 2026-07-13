package io.kestra.webserver.services.ai.agent;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;

import jakarta.inject.Singleton;

/**
 * OSS resolver: no multi-user identity exists (a single BasicAuth account), so there is no principal
 * to capture. EE replaces this with a resolver backed by the authenticated user.
 */
@Singleton
public class DefaultAgentPrincipalResolver implements AgentPrincipalResolver {
    @Override
    public AgentPrincipal resolve() {
        return null;
    }
}
