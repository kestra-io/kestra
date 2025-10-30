package io.kestra.webserver.controllers.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.utils.PosthogUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Objects;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@WireMockTest(httpPort = 28181)
class AiControllerTest {
    @Inject
    @Client("/")
    HttpClient client;

    @RegisterExtension
    static WireMockExtension extension = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .httpsPort(28183)
            .keystorePath(Objects.requireNonNull(AiControllerTest.class.getClassLoader().getResource("mtls/server-keystore.p12")).getPath())
            .keystorePassword("keystorePassword")
            .keyManagerPassword("keystorePassword")
            .keystoreType("PKCS12")
            .needClientAuth(true) // This enables mTLS
            .trustStorePath(Objects.requireNonNull(AiControllerTest.class.getClassLoader().getResource("mtls/client-truststore.p12")).getPath()) // Contains trusted client CAs
            .trustStorePassword("changeit")
            .trustStoreType("PKCS12"))
        .build();

    @BeforeEach
    void baseMocks(WireMockRuntimeInfo wmRuntimeInfo) {
        PosthogUtil.mockPosthog(wmRuntimeInfo);
    }

    @Test
    void mTLS() {
        extension.stubFor(post(anyUrl())
            .inScenario("Regular flow generation")
            .whenScenarioStateIs("Started")
            .willReturn(
                aResponse().withResponseBody(
                    Body.fromJsonBytes("""
                        {
                           "responseId" : "3NvjaPPRAo_WvdIP46DvmQE",
                           "modelVersion" : "gemini-2.5-flash",
                           "candidates" : [ {
                             "content" : {
                               "parts" : [ {
                                 "text" : "io.kestra.plugin.core.log.Log"
                               } ],
                               "role" : "model"
                             },
                             "finishReason" : "STOP",
                             "index" : 0
                           } ],
                           "usageMetadata" : {
                             "promptTokenCount" : 3658,
                             "candidatesTokenCount" : 25,
                             "totalTokenCount" : 3939
                           }
                         }""".getBytes()
            )))
            .willSetStateTo("Tasks fetched"));

        String expectedFlowResponse = "id: my-flow\\nnamespace: io.kestra.tests\\ntasks:\\n  - id: log\\n    type: io.kestra.plugin.core.log.Log\\n    format: \\\"hi\\\"";
        extension.stubFor(post(anyUrl())
            .inScenario("Regular flow generation")
            .whenScenarioStateIs("Tasks fetched")
            .willReturn(
                aResponse().withResponseBody(
                    Body.fromJsonBytes("""
                        {
                           "responseId" : "3NvjaPPRAo_WvdIP46DvmQF",
                           "modelVersion" : "gemini-2.5-flash",
                           "candidates" : [ {
                             "content" : {
                               "parts" : [ {
                                 "text" : "%s"
                               } ],
                               "role" : "model"
                             },
                             "finishReason" : "STOP",
                             "index" : 0
                           } ],
                           "usageMetadata" : {
                             "promptTokenCount" : 3658,
                             "candidatesTokenCount" : 25,
                             "totalTokenCount" : 3939
                           }
                         }""".formatted(expectedFlowResponse).getBytes()
            ))));

        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/ai/generate/flow", new FlowGenerationPrompt(IdUtils.create(), "Say 'hi'", null)),
            String.class
        );

        GetServeEventsResult serveEvents = extension.getServeEvents();

        serveEvents.getServeEvents().forEach(serveEvent -> {
            assertThat(serveEvent.getResponse().getStatus()).isEqualTo(200);
        });

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.getBody().get()).isEqualTo(expectedFlowResponse.replace("\\n", "\n").replace("\\\"", "\""));
    }
}
