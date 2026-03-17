package io.kestra.webserver.services.ai.api;

import io.kestra.core.services.InstanceService;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.UserInfo;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
        when(apiHttpClient.retrieve(requestCaptor.capture(), eq(String.class))).thenReturn("generated-flow");

        String result = apiAiService.generateFlow(userInfo, prompt, "tenant-1");

        assertThat(result).isEqualTo("generated-flow");

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
        when(apiHttpClient.retrieve(requestCaptor.capture(), eq(String.class))).thenReturn("generated-dashboard");

        String result = apiAiService.generateDashboard(userInfo, prompt);

        assertThat(result).isEqualTo("generated-dashboard");

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
