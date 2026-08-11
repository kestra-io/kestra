package io.kestra.webserver.services.ai.gemini;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.AiServiceManager;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the custom headers configured on a provider are sent to the model API, on both the chat and the
 * streaming path. The provider is built by {@link AiServiceManager} from the properties below, so this also covers
 * the header names surviving the configuration binding — Micronaut's camel-case convention would otherwise turn
 * {@code X-Api-Key} into {@code xApiKey}.
 */
@KestraTest
@WireMockTest(httpPort = GeminiAiServiceTest.MODEL_API_PORT)
@Property(name = "kestra.ai.providers[0].id", value = GeminiAiServiceTest.PROVIDER_ID)
@Property(name = "kestra.ai.providers[0].type", value = "gemini")
@Property(name = "kestra.ai.providers[0].configuration.base-url", value = "http://localhost:" + GeminiAiServiceTest.MODEL_API_PORT)
@Property(name = "kestra.ai.providers[0].configuration.model-name", value = "gemini-2.5-flash")
@Property(name = "kestra.ai.providers[0].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[0].configuration.custom-headers.X-Api-Key", value = "secret")
@Property(name = "kestra.ai.providers[0].configuration.custom-headers.X-Gateway-Route", value = "internal")
class GeminiAiServiceTest {
    static final int MODEL_API_PORT = 28184;
    static final String PROVIDER_ID = "gemini-custom-headers";

    // Paths are relative to the configured base URL, which is why they carry no API version prefix.
    private static final String GENERATE_CONTENT_PATH = "/models/.*:generateContent";
    private static final String STREAM_GENERATE_CONTENT_PATH = "/models/.*:streamGenerateContent";

    private static final String RESPONSE = """
        {"candidates":[{"content":{"parts":[{"text":"ok"}],"role":"model"},"finishReason":"STOP"}],\
        "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";

    @Inject
    private AiServiceManager aiServiceManager;

    private AiService<?> configuredProvider() {
        return (AiService<?>) aiServiceManager.getAiService(PROVIDER_ID);
    }

    /** Asserts the model API was called with every configured header. */
    private static void verifyCustomHeadersSentTo(String path) {
        verify(
            postRequestedFor(urlPathMatching(path))
                .withHeader("X-Api-Key", equalTo("secret"))
                .withHeader("X-Gateway-Route", equalTo("internal"))
        );
    }

    @Test
    void shouldSendCustomHeadersOnChatRequests() {
        // Given a model API that answers a chat request
        stubFor(post(urlPathMatching(GENERATE_CONTENT_PATH)).willReturn(okJson(RESPONSE)));

        // When the configured provider is asked to answer a prompt
        configuredProvider().chatModel(List.of()).chat("hi");

        // Then the request carried the configured headers
        verifyCustomHeadersSentTo(GENERATE_CONTENT_PATH);
    }

    @Test
    void shouldSendCustomHeadersOnStreamingChatRequests() throws InterruptedException {
        stubFor(post(urlPathMatching(STREAM_GENERATE_CONTENT_PATH)).willReturn(okJson(RESPONSE)));

        CountDownLatch answered = new CountDownLatch(1);
        configuredProvider().streamingChatModel(List.of()).chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                answered.countDown();
            }

            @Override
            public void onError(Throwable error) {
                answered.countDown();
            }
        });

        assertThat(answered.await(10, TimeUnit.SECONDS)).isTrue();
        verifyCustomHeadersSentTo(STREAM_GENERATE_CONTENT_PATH);
    }
}
