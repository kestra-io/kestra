package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.utils.PosthogUtil;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@KestraTest
@WireMockTest(httpPort = 28181)
class AiControllerTest {
    @Inject
    @Client("/")
    HttpClient client;

    @BeforeEach
    void baseMocks(WireMockRuntimeInfo wmRuntimeInfo) {
        PosthogUtil.mockPosthog(wmRuntimeInfo);
    }

    @Test
    void shouldReturn503WhenProviderNotFound() {
        // Given: no AI provider is configured (OSS no longer falls back to a hosted free tier), so an
        // authoring request has no service to run against and must surface as unavailable.
        HttpClientResponseException exception = catchThrowableOfType(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    "/api/v1/main/ai/generate/flow",
                    new AiController.FlowGenerationPrompt(IdUtils.create(), "Generate a flow", "yaml", "io.kestra.tests", "nonexistent-provider")
                ),
                String.class
            )
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(503);
    }
}
