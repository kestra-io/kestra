package io.kestra.webserver.services.ai.api;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.services.InstanceService;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.UserInfo;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAiServiceTest {
    @Mock
    private BlockingHttpClient apiHttpClient;

    @Mock
    private InstanceService instanceService;

    @Captor
    private ArgumentCaptor<HttpRequest<?>> requestCaptor;

    private ApiAiService apiAiService;

    @BeforeEach
    void setUp() {
        apiAiService = new ApiAiService(apiHttpClient, instanceService);
    }

    @Test
    void generateFlowShouldSendTenantAndUserHeaders() {
        UserInfo userInfo = new UserInfo("192.0.2.10", "user-1");
        FlowGenerationPrompt prompt = new FlowGenerationPrompt("conversation-1", "Generate a flow", "yaml: true", "io.kestra.tests");

        when(instanceService.fetch()).thenReturn("instance-1");
        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.get("X-Kestra-AI-Quota", Integer.class)).thenReturn(Optional.of(42));
        when(httpResponse.body()).thenReturn("generated-flow");
        when(httpResponse.getHeaders()).thenReturn(headers);
        when(apiHttpClient.exchange(requestCaptor.capture(), eq(String.class))).thenReturn(httpResponse);

        GenerationResult result = apiAiService.generateFlow(userInfo, prompt, "tenant-1");

        assertThat(result.content()).isEqualTo("generated-flow");
        assertThat(result.remainingQuota()).hasValue(42);

        HttpRequest<?> request = requestCaptor.getValue();
        assertThat(request.getPath()).isEqualTo("/v1/ai/generate/flow");
        assertThat(request.getMethodName()).isEqualTo("POST");
        assertThat(request.getHeaders().get("X-Kestra-Instance-Id")).isEqualTo("instance-1");
        assertThat(request.getHeaders().get("X-Kestra-User-Id")).isEqualTo("user-1");

        assertThat(request.getBody()).isPresent();
        assertThat(request.getBody().orElseThrow()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) request.getBody().orElseThrow();
        assertThat(body)
            .containsEntry("conversationId", "conversation-1")
            .containsEntry("userPrompt", "Generate a flow")
            .containsEntry("yaml", "yaml: true")
            .containsEntry("namespace", "io.kestra.tests")
            .containsEntry("tenantId", "tenant-1");
    }

    @Test
    void generateDashboardShouldSendPromptAndUserHeaders() {
        UserInfo userInfo = new UserInfo("198.51.100.5", "user-2");
        DashboardGenerationPrompt prompt = new DashboardGenerationPrompt("conversation-2", "Generate a dashboard", "widgets: []");

        when(instanceService.fetch()).thenReturn("instance-2");
        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.get("X-Kestra-AI-Quota", Integer.class)).thenReturn(Optional.empty());
        when(httpResponse.body()).thenReturn("generated-dashboard");
        when(httpResponse.getHeaders()).thenReturn(headers);
        when(apiHttpClient.exchange(requestCaptor.capture(), eq(String.class))).thenReturn(httpResponse);

        GenerationResult result = apiAiService.generateDashboard(userInfo, prompt);

        assertThat(result.content()).isEqualTo("generated-dashboard");
        assertThat(result.remainingQuota()).isEmpty();

        HttpRequest<?> request = requestCaptor.getValue();
        assertThat(request.getPath()).isEqualTo("/v1/ai/generate/dashboard");
        assertThat(request.getMethodName()).isEqualTo("POST");
        assertThat(request.getHeaders().get("X-Kestra-Instance-Id")).isEqualTo("instance-2");
        assertThat(request.getHeaders().get("X-Kestra-User-Id")).isEqualTo("user-2");
        assertThat(request.getBody()).isPresent();
        assertThat(request.getBody().orElseThrow()).isEqualTo(prompt);
    }

    @Test
    void displayNameShouldBeFreeTier() {
        assertThat(apiAiService.displayName()).isEqualTo("Free tier");
    }
}
