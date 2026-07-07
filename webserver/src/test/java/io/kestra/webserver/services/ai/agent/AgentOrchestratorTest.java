package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageType;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.store.MessageStore;
import io.kestra.webserver.services.ai.agent.store.ThreadStore;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
@Property(name = "kestra.ai.agent.model-call-timeout", value = "PT1S")
class AgentOrchestratorTest {
    private static final String TENANT = TenantService.MAIN_TENANT;

    private final ScriptedStreamingChatModel scriptedModel = new ScriptedStreamingChatModel();

    @Inject
    AgentOrchestrator orchestrator;

    @Inject
    ThreadStore threadStore;

    @Inject
    MessageStore messageStore;

    @Inject
    ConfirmationRegistry confirmationRegistry;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        AiServiceInterface service = mock(AiServiceInterface.class);
        when(service.streamingChatModel(any())).thenReturn(scriptedModel);
        AiServiceManager manager = mock(AiServiceManager.class);
        when(manager.getAiService(any())).thenReturn(service);
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
    void shouldStreamAnswerAndPersistMessagesWhenTurnHasNoToolCalls() {
        // Given
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("A trigger starts a flow."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "what is a trigger?", AgentMode.ASK, TENANT, null), sink);

        // Then
        assertThat(sink.names()).containsExactly(AgentEvents.TOKEN, AgentEvents.DONE);
        assertThat(sink.completed).isTrue();
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly("USER/TEXT", "ASSISTANT/TEXT");
    }

    @Test
    void shouldDeriveTitleFromFirstUserMessageWhenTitleAbsent() {
        // Given
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("answer"));

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "How do retries work?", AgentMode.ASK, TENANT, null), new CollectingSink());

        // Then
        assertThat(reload(thread).title()).isEqualTo("How do retries work?");
    }

    @Test
    void shouldExecuteReadToolWithoutConfirmationWhenRunningApprovedPlan() {
        // Given — an approved plan; the model then calls the real AUTO read-execution-logs tool and answers.
        // (In Plan mode a read+answer must run *after* approval — pre-approval, any tool-free response
        // is taken as the plan card, so the AUTO path is only reachable once approved.)
        AgentThread thread = newThread(AgentMode.PLAN);
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read the logs"));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "why did exec-1 fail?", AgentMode.PLAN, TENANT, null), first);
        SuspendedTurn turn = confirmationRegistry.take(confirmationId(first)).orElseThrow();
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "read-execution-logs", "exec-1"))));
        scriptedModel.enqueue(AiMessage.from("The run failed on the load task."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(turn, true, null, sink);

        // Then — the read tool was dispatched (not suspended) and the loop continued to a final answer
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE);
        assertThat(toolResultOutcome(sink)).isEqualTo("ok");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        // the durable log spans both turns: prompt, plan card, approval nudge, tool call, result, answer
        assertThat(messageStore.load(thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly(
                "USER/TEXT", "ASSISTANT/PROPOSED_ACTION", "USER/TEXT",
                "ASSISTANT/TOOL_CALL", "TOOL/TOOL_RESULT", "ASSISTANT/TEXT");
    }

    @Test
    void shouldSuspendAndParkTurnWhenToolRequiresConfirmation() {
        // Given — Edit mode; the model calls the CONFIRM mutate tool
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null), sink);

        // Then — suspended before executing; a turn is parked and the thread awaits confirmation
        assertThat(sink.names()).containsExactly(AgentEvents.PROPOSED_ACTION, AgentEvents.DONE);
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
        assertThat(confirmationRegistry.take(confirmationId(sink))).isPresent();
        assertThat(messageStore.load(thread.uid()))
            .anyMatch(m -> m.type() == AgentMessageType.PROPOSED_ACTION);
    }

    @Test
    void shouldDispatchHeldToolAndFinishWhenActionApproved() {
        // Given — Edit mode; a suspended mutate action awaiting approval, and a closing answer
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null), first);
        SuspendedTurn turn = confirmationRegistry.take(confirmationId(first)).orElseThrow();
        scriptedModel.enqueue(AiMessage.from("Done, I restarted it."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(turn, true, null, sink);

        // Then — the real restart tool ran and the turn finished IDLE
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE);
        assertThat(toolResultOutcome(sink)).isEqualTo("ok");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(m -> "ok".equals(m.toolResult().get("outcome")));
    }

    @Test
    void shouldRecordRejectedResultAndResumeWhenActionRejectedInEditMode() {
        // Given — Edit mode; a suspended mutate action, and a closing answer for the resumed loop
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null), first);
        SuspendedTurn turn = confirmationRegistry.take(confirmationId(first)).orElseThrow();
        scriptedModel.enqueue(AiMessage.from("Okay, I won't restart it."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(turn, false, "leave it", sink);

        // Then — held tool not run; a rejected result is recorded and the loop resumes to IDLE
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(m -> "rejected".equals(m.toolResult().get("outcome")));
    }

    @Test
    void shouldSuspendWithPlanCardWhenFirstResponseHasNoToolCallsInPlanMode() {
        // Given — Plan mode; the first tool-free response is the plan
        AgentThread thread = newThread(AgentMode.PLAN);
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read logs\n2. restart"));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "fix it", AgentMode.PLAN, TENANT, null), sink);

        // Then — a plan card (no tool) awaiting confirmation
        AgentEvents.ProposedActionEvent plan = (AgentEvents.ProposedActionEvent) sink.first(AgentEvents.PROPOSED_ACTION);
        assertThat(plan.tool()).isNull();
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void shouldAbortPlanWhenPlanCardRejected() {
        // Given — a plan awaiting approval
        AgentThread thread = newThread(AgentMode.PLAN);
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read logs\n2. restart"));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "fix it", AgentMode.PLAN, TENANT, null), first);
        SuspendedTurn turn = confirmationRegistry.take(confirmationId(first)).orElseThrow();
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(turn, false, "not now", sink);

        // Then — the plan aborts with a closing note and the thread returns IDLE
        assertThat(sink.names()).containsExactly(AgentEvents.DONE);
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.uid()))
            .anyMatch(m -> m.type() == AgentMessageType.TEXT && m.content() != null && m.content().startsWith("Plan rejected"));
    }

    @Test
    void shouldRejectDisallowedToolAndContinueWhenToolNotInMode() {
        // Given — Ask mode exposes no act tools; the model still tries to restart
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "restart-execution", "exec-1"))));
        scriptedModel.enqueue(AiMessage.from("I can only answer questions here."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.ASK, TENANT, null), sink);

        // Then — the disallowed call is rejected (not dispatched) and the loop continues to an answer
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
    }

    @Test
    void shouldNotCallModelWhenClientAlreadyDisconnected() {
        // Given — the client is already gone before the turn starts
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("should not be consumed"));
        CollectingSink sink = new CollectingSink();
        sink.cancel();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "hi", AgentMode.ASK, TENANT, null), sink);

        // Then — nothing is emitted or errored, and the thread is returned to IDLE
        assertThat(sink.names()).isEmpty();
        assertThat(sink.error).isNull();
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldStopTurnWhenClientDisconnectsMidStream() {
        // Given — the client disconnects while the first response is streaming
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("partial answer"));
        CollectingSink sink = new CollectingSink();
        sink.cancelOnFirstEmit();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "hi", AgentMode.ASK, TENANT, null), sink);

        // Then — the streamed token was delivered, but the turn aborts before DONE (without erroring
        // or looping again) and the thread is returned to IDLE
        assertThat(sink.names()).contains(AgentEvents.TOKEN);
        assertThat(sink.names()).doesNotContain(AgentEvents.DONE);
        assertThat(sink.error).isNull();
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldFailTurnWhenModelCallTimesOut() {
        // Given — the provider accepts the call but never responds
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.hang();
        CollectingSink sink = new CollectingSink();

        // When — the bounded model-call wait (PT1S in tests) elapses
        orchestrator.runTurn(new AgentTurnContext(thread, "hello?", AgentMode.ASK, TENANT, null), sink);

        // Then — the turn fails cleanly with a timeout and the thread is reset to IDLE
        assertThat(sink.error).isNotNull();
        assertThat(sink.error).hasMessageContaining("timed out");
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldResetThreadToIdleAndSurfaceErrorWhenModelFails() {
        // Given — no scripted response, so the model errors
        AgentThread thread = newThread(AgentMode.ASK);
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "hello", AgentMode.ASK, TENANT, null), sink);

        // Then — the turn fails cleanly: error surfaced and the thread is reset to IDLE
        assertThat(sink.error).isNotNull();
        assertThat(sink.completed).isFalse();
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private AgentThread newThread(final AgentMode mode) {
        return threadStore.create(AgentThread.builder()
            .uid(IdUtils.create())
            .tenant(TENANT)
            .mode(mode)
            .status(AgentThreadStatus.IDLE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .deleted(false)
            .build());
    }

    private AgentThread reload(final AgentThread thread) {
        return threadStore.find(TENANT, thread.uid()).orElseThrow();
    }

    private static ToolExecutionRequest toolCall(final String id, final String name, final String executionId) {
        return ToolExecutionRequest.builder()
            .id(id)
            .name(name)
            .arguments("{\"executionId\":\"" + executionId + "\"}")
            .build();
    }

    private static String doneStatus(final CollectingSink sink) {
        return ((AgentEvents.DoneEvent) sink.first(AgentEvents.DONE)).status();
    }

    private static String toolResultOutcome(final CollectingSink sink) {
        return ((AgentEvents.ToolResultEvent) sink.first(AgentEvents.TOOL_RESULT)).outcome();
    }

    private static String confirmationId(final CollectingSink sink) {
        return ((AgentEvents.ProposedActionEvent) sink.first(AgentEvents.PROPOSED_ACTION)).confirmationId();
    }

    private static final class CollectingSink implements TurnEventSink {
        private final List<Map.Entry<String, Object>> events = new ArrayList<>();
        private boolean completed;
        private Throwable error;
        private volatile boolean cancelled;
        private boolean cancelOnFirstEmit;

        void cancel() {
            this.cancelled = true;
        }

        void cancelOnFirstEmit() {
            this.cancelOnFirstEmit = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void emit(final String event, final Object payload) {
            events.add(Map.entry(event, payload));
            if (cancelOnFirstEmit) {
                cancelled = true;
            }
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void error(final Throwable e) {
            error = e;
        }

        private List<String> names() {
            return events.stream().map(Map.Entry::getKey).toList();
        }

        private Object first(final String name) {
            return events.stream()
                .filter(e -> name.equals(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No '" + name + "' event in " + names()));
        }
    }

    private static final class ScriptedStreamingChatModel implements StreamingChatModel {
        private final Deque<AiMessage> responses = new ArrayDeque<>();
        private volatile boolean hang;

        private void enqueue(final AiMessage message) {
            responses.addLast(message);
        }

        private void hang() {
            this.hang = true;
        }

        private void clear() {
            responses.clear();
            this.hang = false;
        }

        @Override
        public void chat(final ChatRequest request, final StreamingChatResponseHandler handler) {
            if (hang) {
                return; // never complete the response -> the orchestrator's bounded wait must time out
            }
            AiMessage ai = responses.pollFirst();
            if (ai == null) {
                handler.onError(new IllegalStateException("No scripted LLM response available"));
                return;
            }
            if (ai.text() != null && !ai.text().isEmpty()) {
                handler.onPartialResponse(ai.text());
            }
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(ai).build());
        }
    }
}
