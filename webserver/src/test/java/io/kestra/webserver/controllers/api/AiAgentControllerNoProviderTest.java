package io.kestra.webserver.controllers.api;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When no AI provider is configured, the Copilot endpoints must reject up front with 503
 * (rather than starting a turn that fails mid-stream on the non-streaming fallback service).
 */
@KestraTest
class AiAgentControllerNoProviderTest {
    private static final String BASE = "/api/v1/" + TenantService.MAIN_TENANT + "/ai/threads";

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        AiServiceManager manager = mock(AiServiceManager.class);
        when(manager.hasConfiguredProvider()).thenReturn(false);
        return manager;
    }

    @MockBean(DocsMcpToolProvider.class)
    DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    @Test
    void shouldReturnServiceUnavailableOnCreateWhenNoProviderConfigured() {
        assertThatThrownBy(
            () -> client.toBlocking().exchange(
                HttpRequest.POST(BASE, new ApiCreateThreadRequest(null, null)), ApiThreadSummary.class
            )
        )
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode())
            );
    }

    @Test
    void shouldReturnServiceUnavailableOnChatWhenNoProviderConfigured() {
        assertThatThrownBy(
            () -> client.toBlocking().exchange(
                HttpRequest.POST(BASE + "/any-thread/chat", new ApiChatTurnRequest("hi", AgentMode.ASK, null, null))
            )
        )
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode())
            );
    }
}
