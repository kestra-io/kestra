package io.kestra.webserver.services.ai.api;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.UserInfo;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ApiAiService implements AiServiceInterface {
    private final BlockingHttpClient apiHttpClient;
    private final InstanceService instanceService;

    public ApiAiService(BlockingHttpClient apiHttpClient, InstanceService instanceService) {
        this.apiHttpClient = apiHttpClient;
        this.instanceService = instanceService;
    }


    @Override
    public String generateFlow(UserInfo userInfo, FlowGenerationPrompt flowGenerationPrompt, String tenantId) {
        Map<String, Object> asMap = new HashMap<>(JacksonMapper.toMap(flowGenerationPrompt));
        asMap.put("tenantId", tenantId);

        return apiHttpClient.retrieve(withUserInfoHeaders(HttpRequest.POST(
            "/v1/ai/generate/flow",
            asMap
        ), userInfo), String.class);
    }

    @Override
    public String generateDashboard(UserInfo userInfo, DashboardGenerationPrompt dashboardGenerationPrompt) {
        return apiHttpClient.retrieve(withUserInfoHeaders(HttpRequest.POST(
            "/v1/ai/generate/dashboard",
            dashboardGenerationPrompt
        ), userInfo), String.class);
    }

    private <B> HttpRequest<B> withUserInfoHeaders(MutableHttpRequest<B> originalRequest, UserInfo userInfo) {
        return originalRequest.headers(Map.of(
            "X-Kestra-Instance-Id", instanceService.fetch(),
            "X-Kestra-User-Id", userInfo.uid()
        ));
    }

    @Override
    public String displayName() {
        return "Free tier";
    }
}
