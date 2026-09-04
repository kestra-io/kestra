package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.agent.AbstractAiAgentTest;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiConfirmActionRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiThreadDetail;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;

import dev.langchain4j.data.message.AiMessage;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * Shared scaffolding for the Copilot HTTP surface: the mocked agent beans from
 * {@link AbstractAiAgentTest} plus the clients and SSE helpers every endpoint test needs. Subclasses
 * declare their own {@code @KestraTest} and any {@code @Property} overrides.
 */
public abstract class AbstractAiAgentControllerTest extends AbstractAiAgentTest {
    protected static final String BASE = "/api/v1/" + TenantService.MAIN_TENANT + "/ai/threads";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(20);

    @Inject
    @Client("/")
    protected HttpClient client;

    @Inject
    @Client("/")
    protected SseClient sseClient;

    protected ApiThreadSummary createThread() {
        return createThread(new ApiCreateThreadRequest(AgentMode.ASK, "q"));
    }

    protected ApiThreadSummary createThread(final ApiCreateThreadRequest request) {
        return client.toBlocking().retrieve(HttpRequest.POST(BASE, request), ApiThreadSummary.class);
    }

    protected ApiThreadDetail getThread(final String threadId) {
        return client.toBlocking().retrieve(HttpRequest.GET(BASE + "/" + threadId), ApiThreadDetail.class);
    }

    protected List<Event<Map>> chat(final String threadId, final ApiChatTurnRequest request) {
        return stream(BASE + "/" + threadId + "/chat", request);
    }

    /** Scripts a single assistant answer, then runs one full chat turn against it. */
    protected List<Event<Map>> chat(final String threadId, final String prompt, final String answer) {
        scriptedModel.enqueue(AiMessage.from(answer));
        return chat(threadId, new ApiChatTurnRequest(prompt, AgentMode.ASK, null, null));
    }

    protected List<Event<Map>> confirm(final String threadId, final ApiConfirmActionRequest request) {
        return stream(BASE + "/" + threadId + "/confirm", request);
    }

    /** Opens an SSE stream and collects every event it emits before completing. */
    protected List<Event<Map>> stream(final String uri, final Object body) {
        HttpRequest<Object> request = HttpRequest.POST(uri, body).accept(MediaType.TEXT_EVENT_STREAM);
        return Flux.from(sseClient.eventStream(request, Argument.of(Map.class)))
            .collectList()
            .block(STREAM_TIMEOUT);
    }

    /** A plain (non-SSE) chat request, for asserting the status a refused turn returns. */
    protected HttpRequest<?> chatRequest(final String threadId, final String prompt) {
        return HttpRequest.POST(BASE + "/" + threadId + "/chat", new ApiChatTurnRequest(prompt, AgentMode.ASK, null, null))
            .accept(MediaType.TEXT_EVENT_STREAM);
    }

    protected static List<String> names(final List<Event<Map>> events) {
        return events.stream().map(Event::getName).toList();
    }

    protected static Map<String, Object> data(final List<Event<Map>> events, final String name) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = events.stream()
            .filter(e -> name.equals(e.getName()))
            .map(e -> (Map<String, Object>) e.getData())
            .findFirst()
            .orElseThrow(() -> new AssertionError("No '" + name + "' event in " + names(events)));
        return payload;
    }

    protected static String doneStatus(final List<Event<Map>> events) {
        return (String) data(events, AgentEvents.DONE).get("status");
    }

    protected static String confirmationId(final List<Event<Map>> events) {
        return (String) data(events, AgentEvents.PROPOSED_ACTION).get("confirmationId");
    }
}
