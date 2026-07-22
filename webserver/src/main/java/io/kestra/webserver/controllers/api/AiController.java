package io.kestra.webserver.controllers.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.tenant.TenantService;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.UserInfo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.server.util.HttpClientAddressResolver;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    @Operation(tags = { "AI" }, summary = "Generate or regenerate a flow based on a prompt")
    public HttpResponse<String> generateFlow(
        @RequestBody(description = "Prompt and context required for flow generation") @Body FlowGenerationPrompt flowGenerationPrompt,
        HttpRequest<?> httpRequest) {
        AiServiceInterface service = aiServiceManager.getAiService(flowGenerationPrompt.getProviderId());
        if (service == null) {
            return HttpResponse.<String> status(HttpStatus.SERVICE_UNAVAILABLE).body("AI Copilot is not available: no AI provider is configured or reachable.");
        }

        try {
            GenerationResult result = service
                .generateFlow(new UserInfo(httpClientAddressResolver.resolve(httpRequest), httpRequest.getHeaders().get("X-Kestra-User-Id")), flowGenerationPrompt, tenantService.resolveTenant());
            return toHttpResponse(result);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI flow generation failed.", e);
            return HttpResponse.<String> status(HttpStatus.SERVICE_UNAVAILABLE).body(providerErrorMessage(e));
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/generate/dashboard", produces = "application/yaml")
    @Operation(tags = { "AI" }, summary = "Generate or regenerate a dashboard based on a prompt")
    public HttpResponse<String> generateDashboard(
        @RequestBody(description = "Prompt and context required for dashboard generation") @Body DashboardGenerationPrompt dashboardGenerationPrompt,
        HttpRequest<?> httpRequest) {
        AiServiceInterface service = aiServiceManager.getAiService(dashboardGenerationPrompt.getProviderId());
        if (service == null) {
            return HttpResponse.<String> status(HttpStatus.SERVICE_UNAVAILABLE).body("AI Copilot is not available: no AI provider is configured or reachable.");
        }

        try {
            GenerationResult result = service
                .generateDashboard(new UserInfo(httpClientAddressResolver.resolve(httpRequest), httpRequest.getHeaders().get("X-Kestra-User-Id")), dashboardGenerationPrompt);
            return toHttpResponse(result);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI dashboard generation failed.", e);
            return HttpResponse.<String> status(HttpStatus.SERVICE_UNAVAILABLE).body(providerErrorMessage(e));
        }
    }

    static String providerErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("authentication") || lower.contains("api key") || lower.contains("apikey")) {
                return "AI provider authentication failed. Please verify that the configured API key is valid and has not expired.";
            }
            if (lower.contains("429") || lower.contains("too many requests") || lower.contains("rate limit") || lower.contains("ratelimit")) {
                return "AI provider rate limit exceeded. Please wait a moment before trying again.";
            }
            if (lower.contains("quota") || lower.contains("insufficient_quota") || lower.contains("billing")) {
                return "AI provider quota exceeded or a billing issue was detected. Please check your provider account.";
            }
        }
        return "AI Copilot failed to generate a response. This may be due to a temporary issue with the AI provider. Please try again.";
    }

    protected HttpResponse<String> toHttpResponse(GenerationResult result) {
        MutableHttpResponse<String> response = HttpResponse.ok(result.content());
        result.remainingQuota().ifPresent(quota -> response.header("X-Kestra-AI-Quota", quota.toString()));
        return response;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "providers")
    @Operation(tags = { "AI" }, summary = "List available AI providers")
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
