package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiConfirmActionRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiDecision;
import io.kestra.webserver.services.ai.agent.data.ApiThreadDetail;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class AiAgentControllerTest {
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
    void shouldCreateIdleThreadWithAskModeWhenModeOmitted() {
        // When
        ApiThreadSummary summary = createThread(new ApiCreateThreadRequest(null, null));

        // Then
        assertThat(summary.mode()).isEqualTo(AgentMode.ASK);
        assertThat(summary.status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(getThread(summary.uid()).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldReturnNotFoundWhenGettingUnknownThread() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().retrieve(HttpRequest.GET(BASE + "/does-not-exist"), ApiThreadDetail.class))
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode())
            );
    }

    @Test
    void shouldStreamAnswerAndFinishIdleWhenChattingInAskMode() {
        // Given
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.ASK, "q"));
        scriptedModel.enqueue(AiMessage.from("A trigger starts a flow automatically."));

        // When
        List<Event<Map>> events = chat(thread.uid(), new ApiChatTurnRequest("what is a trigger?", AgentMode.ASK, null, null));

        // Then
        assertThat(names(events)).contains(AgentEvents.TOKEN, AgentEvents.DONE);
        assertThat(doneStatus(events)).isEqualTo(AgentThreadStatus.IDLE.name());
        ApiThreadDetail detail = getThread(thread.uid());
        assertThat(detail.status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(detail.messages())
            .extracting(m -> m.role() + "/" + m.type())
            .contains("USER/TEXT", "ASSISTANT/TEXT");
    }

    @Test
    void shouldEmitErrorEventAndResetIdleWhenModelCallFailsMidStream() {
        // Given — no scripted response, so the LLM call fails once the stream has started
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.ASK, "q"));

        // When — the stream completes cleanly (no reactive/HTTP error), carrying the failure as an event
        List<Event<Map>> events = chat(thread.uid(), new ApiChatTurnRequest("what is a trigger?", AgentMode.ASK, null, null));

        // Then — an 'error' event surfaces the reason, no 'done' follows, and the thread is reset to IDLE
        assertThat(names(events)).contains(AgentEvents.ERROR).doesNotContain(AgentEvents.DONE);
        assertThat((String) data(events, AgentEvents.ERROR).get("message")).contains("LLM streaming call failed");
        assertThat(getThread(thread.uid()).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldProposeActionAndAwaitConfirmationWhenModelCallsMutateToolInEditMode() {
        // Given
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.EDIT, null));
        scriptedModel.enqueue(mutateToolCall("c1", "exec-1"));

        // When
        List<Event<Map>> events = chat(thread.uid(), new ApiChatTurnRequest("update it", AgentMode.EDIT, null, null));

        // Then — suspended for confirmation, tool NOT executed
        Map<String, Object> proposed = data(events, AgentEvents.PROPOSED_ACTION);
        assertThat(proposed.get("tool")).isEqualTo("update-artefact");
        assertThat(proposed.get("family")).isEqualTo("MUTATE");
        assertThat(doneStatus(events)).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION.name());
        assertThat(getThread(thread.uid()).status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void shouldReturnConflictWhenChattingWhileTurnAwaitsConfirmation() {
        // Given — a thread suspended awaiting confirmation
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.EDIT, null));
        scriptedModel.enqueue(mutateToolCall("c1", "exec-1"));
        chat(thread.uid(), new ApiChatTurnRequest("update it", AgentMode.EDIT, null, null));

        // When / Then — a second turn is rejected with 409
        assertThatThrownBy(
            () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    BASE + "/" + thread.uid() + "/chat",
                    new ApiChatTurnRequest("again", AgentMode.EDIT, null, null)
                )
            )
        )
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode())
            );
    }

    @Test
    void shouldRecordRejectedResultAndResumeIdleWhenRejectingProposedAction() {
        // Given — a proposed mutate awaiting confirmation, and a closing answer to resume into
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.EDIT, null));
        scriptedModel.enqueue(mutateToolCall("c1", "exec-1"));
        String confirmationId = confirmationId(chat(thread.uid(), new ApiChatTurnRequest("update it", AgentMode.EDIT, null, null)));
        scriptedModel.enqueue(AiMessage.from("Understood, I won't update it."));

        // When
        List<Event<Map>> events = confirm(thread.uid(), new ApiConfirmActionRequest(confirmationId, ApiDecision.REJECT, "leave it", null));

        // Then — rejected result surfaced, turn resumed and finished IDLE
        assertThat(data(events, AgentEvents.TOOL_RESULT).get("outcome")).isEqualTo("rejected");
        assertThat(doneStatus(events)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(getThread(thread.uid()).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldProposePlanCardWhenChattingInPlanMode() {
        // Given — Plan mode: the first tool-free response is the plan
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.PLAN, null));
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read the logs\n2. restart the flow"));

        // When
        List<Event<Map>> events = chat(thread.uid(), new ApiChatTurnRequest("fix my failing flow", AgentMode.PLAN, null, null));

        // Then — a plan card (proposed_action with no tool) awaiting confirmation
        assertThat(data(events, AgentEvents.PROPOSED_ACTION).get("tool")).isNull();
        assertThat(doneStatus(events)).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION.name());
        assertThat(getThread(thread.uid()).status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void shouldRunPlanToCompletionWhenApprovingPlanCard() {
        // Given — a plan proposed and awaiting approval, and a closing answer to resume into
        ApiThreadSummary thread = createThread(new ApiCreateThreadRequest(AgentMode.PLAN, null));
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read the logs\n2. restart the flow"));
        String confirmationId = confirmationId(chat(thread.uid(), new ApiChatTurnRequest("fix my failing flow", AgentMode.PLAN, null, null)));
        scriptedModel.enqueue(AiMessage.from("All steps completed."));

        // When
        List<Event<Map>> events = confirm(thread.uid(), new ApiConfirmActionRequest(confirmationId, ApiDecision.APPROVE, null, null));

        // Then
        assertThat(doneStatus(events)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(getThread(thread.uid()).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    private ApiThreadSummary createThread(final ApiCreateThreadRequest request) {
        return client.toBlocking().retrieve(HttpRequest.POST(BASE, request), ApiThreadSummary.class);
    }

    private ApiThreadDetail getThread(final String threadId) {
        return client.toBlocking().retrieve(HttpRequest.GET(BASE + "/" + threadId), ApiThreadDetail.class);
    }

    private List<Event<Map>> chat(final String threadId, final ApiChatTurnRequest request) {
        return stream(BASE + "/" + threadId + "/chat", request);
    }

    private List<Event<Map>> confirm(final String threadId, final ApiConfirmActionRequest request) {
        return stream(BASE + "/" + threadId + "/confirm", request);
    }

    private List<Event<Map>> stream(final String uri, final Object body) {
        HttpRequest<Object> request = HttpRequest.POST(uri, body).accept(MediaType.TEXT_EVENT_STREAM);
        return Flux.from(sseClient.eventStream(request, Argument.of(Map.class)))
            .collectList()
            .block(Duration.ofSeconds(20));
    }

    private static AiMessage mutateToolCall(final String id, final String executionId) {
        return AiMessage.from(
            "", List.of(
                ToolExecutionRequest.builder()
                    .id(id)
                    .name("update-artefact")
                    .arguments("{\"executionId\":\"" + executionId + "\"}")
                    .build()
            )
        );
    }

    private static List<String> names(final List<Event<Map>> events) {
        return events.stream().map(Event::getName).toList();
    }

    private static Map<String, Object> data(final List<Event<Map>> events, final String name) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = events.stream()
            .filter(e -> name.equals(e.getName()))
            .map(e -> (Map<String, Object>) e.getData())
            .findFirst()
            .orElseThrow(() -> new AssertionError("No '" + name + "' event in " + names(events)));
        return payload;
    }

    private static String doneStatus(final List<Event<Map>> events) {
        return (String) data(events, AgentEvents.DONE).get("status");
    }

    private static String confirmationId(final List<Event<Map>> events) {
        return (String) data(events, AgentEvents.PROPOSED_ACTION).get("confirmationId");
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
