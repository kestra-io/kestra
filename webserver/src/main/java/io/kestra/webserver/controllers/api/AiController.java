package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.UserInfo;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.server.util.HttpClientAddressResolver;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Validated
@Controller("/api/v1/main/ai")
@Requires(bean = AiServiceManager.class)
public class AiController {
    @Inject
    protected AiServiceManager aiServiceManager;

    @Inject
    protected HttpClientAddressResolver httpClientAddressResolver;

    @Inject
    protected TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/generate/flow", produces = "application/yaml")
    @Operation(tags = {"AI"}, summary = "Generate or regenerate a flow based on a prompt")
    public HttpResponse<String> generateFlow(
        @RequestBody(description = "Prompt and context required for flow generation") @Body FlowGenerationPrompt flowGenerationPrompt,
        HttpRequest<?> httpRequest
    ) {
        AiServiceInterface service = aiServiceManager.getAiService(flowGenerationPrompt.getProviderId());

        GenerationResult result = service.generateFlow(new UserInfo(httpClientAddressResolver.resolve(httpRequest), httpRequest.getHeaders().get("X-Kestra-User-Id")), flowGenerationPrompt, tenantService.resolveTenant());
        return toHttpResponse(result);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/generate/dashboard", produces = "application/yaml")
    @Operation(tags = {"AI"}, summary = "Generate or regenerate a dashboard based on a prompt")
    public HttpResponse<String> generateDashboard(
        @RequestBody(description = "Prompt and context required for dashboard generation") @Body DashboardGenerationPrompt dashboardGenerationPrompt,
        HttpRequest<?> httpRequest
    ) {
        AiServiceInterface service = aiServiceManager.getAiService(dashboardGenerationPrompt.getProviderId());

        GenerationResult result = service.generateDashboard(new UserInfo(httpClientAddressResolver.resolve(httpRequest), httpRequest.getHeaders().get("X-Kestra-User-Id")), dashboardGenerationPrompt);
        return toHttpResponse(result);
    }

    protected HttpResponse<String> toHttpResponse(GenerationResult result) {
        MutableHttpResponse<String> response = HttpResponse.ok(result.content());
        result.remainingQuota().ifPresent(quota -> response.header("X-Kestra-AI-Quota", quota.toString()));
        return response;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "providers")
    @Operation(tags = {"AI"}, summary = "List available AI providers")
    public List<AiProviderResponse> getProviders() {
        List<AiProviderResponse> response = new ArrayList<>();
        for (Map.Entry<String, AiServiceInterface> entry : aiServiceManager.getAllAiServices().entrySet()) {
            response.add(new AiProviderResponse(entry.getKey(), entry.getValue().displayName(), entry.getKey().equals(aiServiceManager.getDefaultProviderId())));
        }
        response.sort((a, b) -> Boolean.compare(b.isDefault(), a.isDefault()));
        return response;
    }

    public record AiProviderResponse(String id, String displayName, boolean isDefault) {
    }

    @Getter
    public static class FlowGenerationPrompt extends io.kestra.libs.copilot.models.in.FlowGenerationPrompt {
        private final String providerId;

        @JsonCreator
        public FlowGenerationPrompt(String conversationId, String userPrompt, String yaml, String namespace, String providerId) {
            super(conversationId, userPrompt, yaml, namespace);

            this.providerId = providerId;
        }
    }

    @Getter
    public static class DashboardGenerationPrompt extends io.kestra.libs.copilot.models.in.DashboardGenerationPrompt {
        private final String providerId;

        @JsonCreator
        public DashboardGenerationPrompt(String conversationId, String userPrompt, String yaml, String providerId) {
            super(conversationId, userPrompt, yaml);

            this.providerId = providerId;
        }
    }
}
