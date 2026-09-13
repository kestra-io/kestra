package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@code maxTurnsPerThread} cost/abuse guardrail: once a thread has held its maximum number
 * of user turns, a new chat turn is refused. Cap is set to 1 here so the second turn is refused.
 */
@KestraTest
@Property(name = "kestra.ai.agent.max-turns-per-thread", value = "1")
class AiAgentControllerTurnLimitTest {
    private static final String BASE = "/api/v1/" + TenantService.MAIN_TENANT + "/ai/threads";

    private final ScriptedStreamingChatModel scriptedModel = new ScriptedStreamingChatModel();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Client("/")
    SseClient sseClient;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        AiServiceInterface service = mock(AiServiceInterface.class);
        when(service.streamingChatModel(any())).thenReturn(scriptedModel);
        AiServiceManager manager = mock(AiServiceManager.class);
        when(manager.getAiService(any())).thenReturn(service);
        when(manager.hasConfiguredProvider()).thenReturn(true);
        return manager;
    }

    @MockBean(DocsMcpToolProvider.class)
    DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    @BeforeEach
    void resetScript() {
        scriptedModel.clear();
    }

    @Test
    void shouldRefuseNewTurnWhenThreadTurnCapReached() {
        // Given — a thread that has already run one full turn (the cap)
        ApiThreadSummary thread = client.toBlocking().retrieve(
            HttpRequest.POST(BASE, new ApiCreateThreadRequest(AgentMode.ASK, "q")), ApiThreadSummary.class
        );
        scriptedModel.enqueue(AiMessage.from("first answer"));
        Flux.from(
            sseClient.eventStream(
                HttpRequest.POST(BASE + "/" + thread.uid() + "/chat", new ApiChatTurnRequest("first?", AgentMode.ASK, null, null))
                    .accept(MediaType.TEXT_EVENT_STREAM),
                Argument.of(Map.class)
            )
        ).collectList().block(Duration.ofSeconds(20));

        // When — a second turn is attempted on the same thread
        scriptedModel.enqueue(AiMessage.from("second answer"));
        HttpRequest<?> second = HttpRequest.POST(BASE + "/" + thread.uid() + "/chat", new ApiChatTurnRequest("second?", AgentMode.ASK, null, null))
            .accept(MediaType.TEXT_EVENT_STREAM);
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(second, String.class)
        );

        // Then — refused with 429 TOO_MANY_REQUESTS
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.getCode());
    }

    /** A {@link StreamingChatModel} that replays queued assistant messages, one per model call. */
    private static final class ScriptedStreamingChatModel implements StreamingChatModel {
        private final Deque<AiMessage> responses = new ArrayDeque<>();

        void enqueue(final AiMessage message) {
            responses.addLast(message);
        }

        void clear() {
            responses.clear();
        }

        @Override
        public void chat(final ChatRequest request, final StreamingChatResponseHandler handler) {
            AiMessage ai = responses.pollFirst();
            if (ai == null) {
                handler.onError(new IllegalStateException("No scripted LLM response available for this call"));
                return;
            }
            if (ai.text() != null && !ai.text().isEmpty()) {
                handler.onPartialResponse(ai.text());
            }
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(ai).build());
        }
    }
}
