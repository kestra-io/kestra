package io.kestra.webserver.services.ai.openai;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.AiServiceManager;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@WireMockTest(httpPort = OpenAiAiServiceTest.MODEL_API_PORT)
@Property(name = "kestra.ai.providers[0].id", value = OpenAiAiServiceTest.EFFORT_NONE_PROVIDER_ID)
@Property(name = "kestra.ai.providers[0].type", value = "openai")
@Property(name = "kestra.ai.providers[0].configuration.base-url", value = "http://localhost:" + OpenAiAiServiceTest.MODEL_API_PORT + "/none")
@Property(name = "kestra.ai.providers[0].configuration.model-name", value = "gpt-reasoning")
@Property(name = "kestra.ai.providers[0].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[0].configuration.thinking-effort", value = "none")
@Property(name = "kestra.ai.providers[1].id", value = OpenAiAiServiceTest.EFFORT_HIGH_PROVIDER_ID)
@Property(name = "kestra.ai.providers[1].type", value = "openai")
@Property(name = "kestra.ai.providers[1].configuration.base-url", value = "http://localhost:" + OpenAiAiServiceTest.MODEL_API_PORT + "/high")
@Property(name = "kestra.ai.providers[1].configuration.model-name", value = "gpt-reasoning")
@Property(name = "kestra.ai.providers[1].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[1].configuration.thinking-effort", value = "high")
class OpenAiAiServiceTest {
    static final int MODEL_API_PORT = 28185;
    static final String EFFORT_NONE_PROVIDER_ID = "openai-effort-none";
    static final String EFFORT_HIGH_PROVIDER_ID = "openai-effort-high";

    private static final String STREAM_RESPONSE = """
        data: {"id":"1","object":"chat.completion.chunk","created":0,"model":"gpt-reasoning","choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}

        data: [DONE]

        """;

    @Inject
    private AiServiceManager aiServiceManager;

    @Test
    void shouldSendReasoningEffortNoneWhenThinkingIsDisabled() throws InterruptedException {
        streamChat(EFFORT_NONE_PROVIDER_ID, "/none/chat/completions");

        verify(postRequestedFor(urlPathEqualTo("/none/chat/completions"))
            .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("none"))));
    }

    @Test
    void shouldOmitReasoningEffortWhenThinkingIsDisabled() throws InterruptedException {
        streamChat(EFFORT_HIGH_PROVIDER_ID, "/high/chat/completions");

        verify(postRequestedFor(urlPathEqualTo("/high/chat/completions"))
            .withRequestBody(notContaining("reasoning_effort")));
    }

    private void streamChat(String providerId, String path) throws InterruptedException {
        stubFor(post(urlPathEqualTo(path)).willReturn(aResponse()
            .withHeader("Content-Type", "text/event-stream")
            .withBody(STREAM_RESPONSE)));

        CountDownLatch answered = new CountDownLatch(1);
        aiServiceManager.getAiService(providerId).streamingChatModel(List.of()).chat("hi", new StreamingChatResponseHandler() {
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
    }
}
