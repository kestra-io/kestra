package io.kestra.webserver.controllers.api;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.utils.PosthogUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = {"api-ai"})
@WireMockTest(httpPort = 28181)
class AiControllerQuotaTest {
    @Inject
    @Client("/")
    protected HttpClient client;

    @BeforeEach
    void baseMocks(WireMockRuntimeInfo wmRuntimeInfo) {
        PosthogUtil.mockPosthog(wmRuntimeInfo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flow", "dashboard"})
    void shouldForwardQuotaHeader(String entityType) {
        stubFor(post(urlPathEqualTo("/v1/ai/generate/" + entityType))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("X-Kestra-AI-Quota", "42")
                .withBody("generated: " + entityType)));

        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/ai/generate/" + entityType, requestBody(entityType)).header("X-Kestra-User-Id", "user-100"),
            String.class
        );

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("generated: " + entityType);
        assertThat(response.header("X-Kestra-AI-Quota")).isEqualTo("42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"flow", "dashboard"})
    void shouldNotIncludeQuotaHeaderWhenAbsent(String entityType) {
        stubFor(post(urlPathEqualTo("/v1/ai/generate/" + entityType))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("generated: " + entityType)));

        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/ai/generate/" + entityType, requestBody(entityType)).header("X-Kestra-User-Id", "user-100"),
            String.class
        );

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.header("X-Kestra-AI-Quota")).isNull();
    }

    private Object requestBody(String entityType) {
        return switch (entityType) {
            case "flow" -> new AiController.FlowGenerationPrompt(IdUtils.create(), "prompt", "", "io.kestra.tests", null);
            case "dashboard" -> new AiController.DashboardGenerationPrompt(IdUtils.create(), "prompt", "", null);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }
}
