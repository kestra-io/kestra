package io.kestra.webserver.controllers.api;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.tenant.TenantService;
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

/**
 * Reads a provider's AI spend against its ceiling.
 *
 * <p>The reason the store is durable rather than in memory: a user can be shown what is left before running a
 * turn, instead of learning it from a header on the turn that spent it, and every node of a scaled webserver
 * answers the same figure.
 */
@Controller("/api/v1/{tenant}/ai/usage")
@Requires(bean = AiServiceManager.class)
public class AiUsageController {
    private final TenantService tenantService;
    private final AiUsageService usageService;
    private final AgentPrincipalResolver principalResolver;

    @Inject
    public AiUsageController(
        final TenantService tenantService,
        final AiUsageService usageService,
        final AgentPrincipalResolver principalResolver) {
        this.tenantService = tenantService;
        this.usageService = usageService;
        this.principalResolver = principalResolver;
    }

    /**
     * Where the caller stands against a provider's ceiling.
     *
     * <p>Answers {@code enabled: false} when the provider declares no limit, which is the default: usage is being
     * recorded either way, but nothing is reported that an operator did not ask to be held to.
     *
     * @param providerId the provider to report on, defaulting to the active one
     */
    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "AI" }, summary = "Get AI usage against the configured limit")
    public AiUsageStatus usage(@QueryValue @Nullable final String providerId) {
        AgentPrincipal principal = principalResolver.resolve();
        return usageService.status(
            tenantService.resolveTenant(),
            providerId,
            principal == null ? null : principal.userId()
        );
    }
}
