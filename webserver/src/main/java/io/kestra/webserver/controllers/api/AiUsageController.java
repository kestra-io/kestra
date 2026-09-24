package io.kestra.webserver.controllers.api;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.AiUsageService;
import io.kestra.webserver.services.ai.AiUsageStatus;
import io.kestra.webserver.services.ai.agent.AgentPrincipalResolver;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;

/** Reads a provider's AI spend against its ceiling. */
@Controller("/api/v1/{tenant}/ai/usage")
@Requires(bean = AiServiceManager.class)
public class AiUsageController {
    private final AiUsageService usageService;
    private final AgentPrincipalResolver principalResolver;

    @Inject
    public AiUsageController(
        final AiUsageService usageService,
        final AgentPrincipalResolver principalResolver) {
        this.usageService = usageService;
        this.principalResolver = principalResolver;
    }

    /**
     * Where the caller stands against a provider's ceiling. Answers {@code enabled: false} when the provider
     * declares no limit, which is the default; usage is recorded either way.
     *
     * @param providerId the provider to report on, defaulting to the active one
     */
    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "AI" }, summary = "Get AI usage against the configured limit")
    public AiUsageStatus usage(@QueryValue @Nullable final String providerId) {
        AgentPrincipal principal = principalResolver.resolve();
        return usageService.status(providerId, principal == null ? null : principal.userId());
    }
}
