package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.AgentOrchestrator;
import io.kestra.webserver.services.ai.agent.TurnEventSink;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@code maxConcurrentTurns} per-node concurrency guardrail: with the cap set to 1, while one
 * turn is in flight (holding the single permit) a second concurrent turn is rejected with 429 rather than
 * queued, and once the in-flight turn finishes the permit is recovered so a later turn succeeds.
 */
@KestraTest
@Property(name = "kestra.ai.agent.max-concurrent-turns", value = "1")
class AiAgentControllerConcurrencyTest {
    private static final String BASE = "/api/v1/" + TenantService.MAIN_TENANT + "/ai/threads";

    /** Signals that the in-flight turn has started (permit acquired); released once the test lets it finish. */
    private final CountDownLatch turnStarted = new CountDownLatch(1);
    private final CountDownLatch releaseTurn = new CountDownLatch(1);

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Client("/")
    SseClient sseClient;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        AiServiceInterface service = mock(AiServiceInterface.class);
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

    /**
     * A latch-blocked orchestrator: every turn blocks on {@link #releaseTurn} while holding its permit, so
     * the test can pin the single permit and probe the gate deterministically.
     */
    @MockBean(AgentOrchestrator.class)
    AgentOrchestrator orchestrator() throws Exception {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        doAnswer(invocation -> {
            TurnEventSink sink = invocation.getArgument(1);
            turnStarted.countDown();
            releaseTurn.await();
            sink.emit("message", Map.of("text", "done"));
            sink.complete();
            return null;
        }).when(orchestrator).runTurn(any(), any());
        return orchestrator;
    }

    @Test
    void shouldRejectSecondConcurrentTurnWith429AndRecoverPermitAfterCompletion() throws Exception {
        // Given — three threads and one turn taken in-flight on thread A, holding the single permit
        ApiThreadSummary threadA = createThread();
        ApiThreadSummary threadB = createThread();
        ApiThreadSummary threadC = createThread();

        Thread firstTurn = new Thread(() ->
            Flux.from(
                sseClient.eventStream(
                    HttpRequest.POST(BASE + "/" + threadA.uid() + "/chat", new ApiChatTurnRequest("first?", AgentMode.ASK, null, null))
                        .accept(MediaType.TEXT_EVENT_STREAM),
                    Argument.of(Map.class)
                )
            ).collectList().block(Duration.ofSeconds(20))
        );
        firstTurn.start();
        assertThat(turnStarted.await(10, TimeUnit.SECONDS)).isTrue();

        // When — a second concurrent turn is attempted on a different thread while the node is at capacity
        HttpRequest<?> second = HttpRequest.POST(BASE + "/" + threadB.uid() + "/chat", new ApiChatTurnRequest("second?", AgentMode.ASK, null, null))
            .accept(MediaType.TEXT_EVENT_STREAM);
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(second, String.class)
        );

        // Then — refused with 429 TOO_MANY_REQUESTS rather than queued
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.getCode());

        // When — the in-flight turn completes, its permit is released
        releaseTurn.countDown();
        firstTurn.join(Duration.ofSeconds(20).toMillis());
        assertThat(firstTurn.isAlive()).isFalse();

        // Then — a subsequent turn succeeds, proving the permit was recovered (release happens on the
        // executor thread just after the stream completes, so poll until the gate frees up).
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() -> {
            List<?> events = Flux.from(
                sseClient.eventStream(
                    HttpRequest.POST(BASE + "/" + threadC.uid() + "/chat", new ApiChatTurnRequest("third?", AgentMode.ASK, null, null))
                        .accept(MediaType.TEXT_EVENT_STREAM),
                    Argument.of(Map.class)
                )
            ).collectList().block(Duration.ofSeconds(20));
            assertThat(events).isNotEmpty();
        });
    }

    private ApiThreadSummary createThread() {
        return client.toBlocking().retrieve(
            HttpRequest.POST(BASE, new ApiCreateThreadRequest(AgentMode.ASK, "q")), ApiThreadSummary.class
        );
    }
}
