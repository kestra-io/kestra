package io.kestra.webserver.services.ai.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.UserInfo;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiAiService implements AiServiceInterface {
    private static final String QUOTA_HEADER = "X-Kestra-AI-Quota";

    private final BlockingHttpClient apiHttpClient;
    private final InstanceService instanceService;

    public ApiAiService(BlockingHttpClient apiHttpClient, InstanceService instanceService) {
        this.apiHttpClient = apiHttpClient;
        this.instanceService = instanceService;
    }

    @Override
    public GenerationResult generateFlow(UserInfo userInfo, FlowGenerationPrompt flowGenerationPrompt, String tenantId) {
        Map<String, Object> asMap = new HashMap<>(JacksonMapper.toMap(flowGenerationPrompt));
        asMap.put("tenantId", tenantId);

        HttpResponse<String> response = apiHttpClient.exchange(
            withUserInfoHeaders(
                HttpRequest.POST(
                    "/v1/ai/generate/flow",
                    asMap
                ), userInfo
            ), String.class
        );

        return toResult(response);
    }

    @Override
    public GenerationResult generateDashboard(UserInfo userInfo, DashboardGenerationPrompt dashboardGenerationPrompt) {
        HttpResponse<String> response = apiHttpClient.exchange(
            withUserInfoHeaders(
                HttpRequest.POST(
                    "/v1/ai/generate/dashboard",
                    dashboardGenerationPrompt
                ), userInfo
            ), String.class
        );

        return toResult(response);
    }

    private <B> HttpRequest<B> withUserInfoHeaders(MutableHttpRequest<B> originalRequest, UserInfo userInfo) {
        return originalRequest.headers(
            Map.of(
                "X-Kestra-Instance-Id", instanceService.fetch(),
                "X-Kestra-User-Id", userInfo.uid(),
                "X-Client-IP", userInfo.ip()
            )
        );
    }

    private GenerationResult toResult(HttpResponse<String> response) {
        Optional<Integer> remainingQuota = response.getHeaders()
            .get(QUOTA_HEADER, Integer.class);
        return new GenerationResult(response.body(), remainingQuota);
    }

    @Override
    public String displayName() {
        return "Free tier";
    }
}
