package io.kestra.webserver.services.ai.agent;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the AI Copilot agent, under {@code kestra.ai.agent}.
 *
 * @param modelCallTimeout how long a single streaming model call may run before the turn is failed,
 *                         so a hung provider cannot pin an executor thread (default 5 minutes).
 * @param docsMcpUrl       the Kestra docs MCP endpoint used for Ask-mode grounding.
 */
@ConfigurationProperties("kestra.ai.agent")
public record AgentConfiguration(
    @Bindable(defaultValue = "PT5M") Duration modelCallTimeout,
    @Bindable(defaultValue = "https://api.kestra.io/v1/mcp") String docsMcpUrl) {
}
