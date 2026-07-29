package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.repositories.AiMessageRepositoryInterface;
import io.kestra.core.ai.agent.repositories.AiThreadRepositoryInterface;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.tool.AgentToolPermissionEvaluator;
import io.kestra.webserver.services.ai.agent.tool.DefaultAgentToolPermissionEvaluator;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

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
    AiThreadRepositoryInterface threadStore;

    @Inject
    AiMessageRepositoryInterface messageStore;

    @Inject
    AiThreadManager threadManager;

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

    private final Set<String> deniedTools = ConcurrentHashMap.newKeySet();

    @MockBean(DefaultAgentToolPermissionEvaluator.class)
    AgentToolPermissionEvaluator permissionEvaluator() {
        return (entry, tenant, principal) -> !deniedTools.contains(entry.name());
    }

    @BeforeEach
    void resetScript() {
        scriptedModel.clear();
        deniedTools.clear();
    }

    @Test
    void shouldStreamAnswerAndPersistMessagesWhenTurnHasNoToolCalls() {
        // Given
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("A trigger starts a flow."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "what is a trigger?", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then
        assertThat(sink.names()).containsExactly(AgentEvents.TOKEN, AgentEvents.DONE);
        assertThat(sink.completed).isTrue();
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly("USER/TEXT", "ASSISTANT/TEXT");
        // the resolved system prompt is sent first (via SystemPromptResolver), with the shared common
        // prompt appended to it (the fenced-code-block guidance from common.md)
        List<ChatMessage> sent = scriptedModel.lastRequestMessages();
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sent.get(0)).text())
            .isNotBlank()
            .contains("fenced");
    }

    @Test
    void shouldNotPersistAssistantMessageWhenModelReturnsEmptyResponse() {
        // Given — the model returns an empty response (no text, no tool calls), as Gemini
        // intermittently does with finishReason=STOP
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from(""));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "what is a trigger?", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the turn ends IDLE, and the empty assistant message is NOT persisted (only the user
        // prompt remains), so it cannot be replayed as an empty model turn on the next turn
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly("USER/TEXT");
    }

    @Test
    void shouldAppendAdditionalContextAsLastModelMessageWithoutPersistingItWhenProvided() {
        // Given — a turn carrying caller-supplied additional context
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("You are on the flow editor."));
        CollectingSink sink = new CollectingSink();
        Map<String, Object> additionalContext = Map.of(
            "view", "flow-editor",
            "namespace", "company.team",
            "flowId", "hello"
        );

        // When
        orchestrator.runTurn(
            new AgentTurnContext(thread, "what am I looking at?", AgentMode.ASK, TENANT, null, null, additionalContext), sink
        );

        // Then — the context is rendered as the LAST message the model receives...
        List<ChatMessage> sent = scriptedModel.lastRequestMessages();
        ChatMessage last = sent.get(sent.size() - 1);
        assertThat(last).isInstanceOf(UserMessage.class);
        String lastText = ((UserMessage) last).singleText();
        assertThat(lastText)
            .contains("Additional context")
            .contains("flow-editor")
            .contains("company.team")
            .contains("hello");

        // ...the user's prompt is still sent, ahead of the context message...
        assertThat(sent)
            .filteredOn(UserMessage.class::isInstance)
            .anySatisfy(m -> assertThat(((UserMessage) m).singleText()).isEqualTo("what am I looking at?"));

        // ...and the context is NOT persisted to the thread history (only the prompt and the answer are)
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly("USER/TEXT", "ASSISTANT/TEXT");
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .noneSatisfy(m -> assertThat(m.content()).contains("Additional context"));
    }

    @Test
    void shouldDeriveTitleFromFirstUserMessageWhenTitleAbsent() {
        // Given
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("answer"));

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "How do retries work?", AgentMode.ASK, TENANT, null, null, null), new CollectingSink());

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
        orchestrator.runTurn(new AgentTurnContext(thread, "why did exec-1 fail?", AgentMode.PLAN, TENANT, null, null, null), first);
        AgentThread awaiting = reload(thread);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "read-execution-logs", "exec-1"))));
        scriptedModel.enqueue(AiMessage.from("The run failed on the load task."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(claim(awaiting), null, true, null, null, sink);

        // Then — the read tool was dispatched (not suspended) and the loop continued to a final answer
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE
        );
        assertThat(toolResultOutcome(sink)).isEqualTo("ok");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        // the durable log spans both turns: prompt, plan card, approval nudge, tool call, result, answer
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly(
                "USER/TEXT", "ASSISTANT/PROPOSED_ACTION", "USER/TEXT",
                "ASSISTANT/TOOL_CALL", "TOOL/TOOL_RESULT", "ASSISTANT/TEXT"
            );
    }

    @Test
    void shouldSuspendAndParkTurnWhenToolRequiresConfirmation() {
        // Given — Edit mode; the model calls the CONFIRM mutate tool
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null, null, null), sink);

        // Then — suspended before executing; a turn is parked and the thread awaits confirmation
        assertThat(sink.names()).containsExactly(AgentEvents.PROPOSED_ACTION, AgentEvents.DONE);
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
        // the confirmation token is persisted on the thread (not held in memory), so any node can resume
        assertThat(reload(thread).pendingConfirmationId()).isEqualTo(confirmationId(sink));
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .anyMatch(m -> m.type() == AgentMessageType.PROPOSED_ACTION);
    }

    @Test
    void shouldDispatchHeldToolAndFinishWhenActionApproved() {
        // Given — Edit mode; a suspended mutate action awaiting approval, and a closing answer
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null, null, null), first);
        AgentThread awaiting = reload(thread);
        scriptedModel.enqueue(AiMessage.from("Done, I restarted it."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(claim(awaiting), null, true, null, null, sink);

        // Then — the real restart tool ran and the turn finished IDLE
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE
        );
        assertThat(toolResultOutcome(sink)).isEqualTo("ok");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(m -> "ok".equals(m.toolResult().get("outcome")));
    }

    @Test
    void shouldRecordRejectedResultAndResumeWhenActionRejectedInEditMode() {
        // Given — Edit mode; a suspended mutate action, and a closing answer for the resumed loop
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.EDIT, TENANT, null, null, null), first);
        AgentThread awaiting = reload(thread);
        scriptedModel.enqueue(AiMessage.from("Okay, I won't restart it."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(claim(awaiting), null, false, "leave it", null, sink);

        // Then — held tool not run; a rejected result is recorded and the loop resumes to IDLE
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(m -> "rejected".equals(m.toolResult().get("outcome")));
    }

    @Test
    void shouldPublishArtefactDraftWhenAuthoringToolProducesDraft() {
        // Given — ASK mode: authoring tools are non-mutating drafts, so even Ask can draft
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "draft-artefact", "exec-1"))));
        scriptedModel.enqueue(AiMessage.from("Here is a draft for you to review."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "draft a flow", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the draft is streamed between the tool call and its result, and the turn finishes IDLE
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.ARTEFACT_DRAFT, AgentEvents.TOOL_RESULT,
            AgentEvents.TOKEN, AgentEvents.DONE
        );
        AgentEvents.ToolCallEvent call = (AgentEvents.ToolCallEvent) sink.first(AgentEvents.TOOL_CALL);
        assertThat(call.kind()).isEqualTo("AUTHORING");
        assertThat(call.family()).isNull();
        AgentEvents.ArtefactDraftEvent draft = (AgentEvents.ArtefactDraftEvent) sink.first(AgentEvents.ARTEFACT_DRAFT);
        assertThat(draft.draftId()).isEqualTo("draft-exec-1");
        assertThat(draft.kind()).isEqualTo("FLOW");
        assertThat(draft.valid()).isTrue();
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        // the draft is durable: it survives in the message log for history reloads
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly(
                "USER/TEXT",
                "ASSISTANT/TOOL_CALL", "ASSISTANT/ARTEFACT_DRAFT", "TOOL/TOOL_RESULT",
                "ASSISTANT/TEXT"
            );
        // authoring calls are recorded with the AUTHORING discriminator and no family
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_CALL)
            .allMatch(m -> m.toolCall().kind() == AgentToolCall.Kind.AUTHORING && m.toolCall().family() == null);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.ARTEFACT_DRAFT)
            .allMatch(m -> m.draft() != null && "draft-exec-1".equals(m.draft().draftId()));
    }

    @Test
    void shouldRejectToolWhenCallerLacksPermission() {
        // Given — Ask mode; the caller lacks the logs permission, so the tool is not advertised —
        // and even when the model calls it anyway, it is rejected without running
        AgentThread thread = newThread(AgentMode.ASK);
        deniedTools.add("read-execution-logs");
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "read-execution-logs", "exec-1"))));
        scriptedModel.enqueue(AiMessage.from("I don't have access to execution logs."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "read the logs of exec-1", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — rejected result recorded, loop continues to an answer
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
    }

    @Test
    void shouldRejectHeldActionWhenPermissionRevokedBeforeConfirmation() {
        // Given — Edit mode; a mutate action is proposed while permitted, then the permission is
        // revoked before the user approves (the pre-filter passed, so only dispatch can catch it)
        AgentThread thread = newThread(AgentMode.EDIT);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "update-artefact", "exec-1"))));
        CollectingSink first = new CollectingSink();
        orchestrator.runTurn(new AgentTurnContext(thread, "update it", AgentMode.EDIT, TENANT, null, null, null), first);
        AgentThread awaiting = reload(thread);
        deniedTools.add("update-artefact");
        scriptedModel.enqueue(AiMessage.from("I could not perform the update: permission denied."));
        CollectingSink sink = new CollectingSink();

        // When — the user approves, but dispatch (the enforcement point) denies
        orchestrator.resume(claim(awaiting), null, true, null, null, sink);

        // Then — the tool never ran; a rejected result is recorded and the loop continues to IDLE
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE
        );
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        // the streamed event carries the rejection reason to the frontend (not just the outcome)
        assertThat(toolResult(sink).reason()).contains("Permission denied");
        assertThat(toolResult(sink).error()).isNull();
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(
                m -> "rejected".equals(m.toolResult().get("outcome"))
                    && String.valueOf(m.toolResult().get("reason")).contains("Permission denied")
            );
    }

    @Test
    void shouldSuspendWithPlanCardWhenFirstResponseHasNoToolCallsInPlanMode() {
        // Given — Plan mode; the first tool-free response is the plan
        AgentThread thread = newThread(AgentMode.PLAN);
        scriptedModel.enqueue(AiMessage.from("Plan:\n1. read logs\n2. restart"));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "fix it", AgentMode.PLAN, TENANT, null, null, null), sink);

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
        orchestrator.runTurn(new AgentTurnContext(thread, "fix it", AgentMode.PLAN, TENANT, null, null, null), first);
        AgentThread awaiting = reload(thread);
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.resume(claim(awaiting), null, false, "not now", null, sink);

        // Then — the plan aborts with a closing note and the thread returns IDLE
        assertThat(sink.names()).containsExactly(AgentEvents.DONE);
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
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
        orchestrator.runTurn(new AgentTurnContext(thread, "restart it", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the disallowed call is rejected (not dispatched) and the loop continues to an answer
        assertThat(toolResultOutcome(sink)).isEqualTo("rejected");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
    }

    @Test
    void shouldClaimThreadForExactlyOneConcurrentTurn() throws Exception {
        // Given — an idle thread and many attempts racing to start a turn on it
        AgentThread thread = newThread(AgentMode.ASK);
        int attempts = 16;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<AgentThread>>> claims = new ArrayList<>();

        // When — all attempts try to claim IDLE -> RUNNING at once
        for (int i = 0; i < attempts; i++) {
            claims.add(pool.submit(() ->
            {
                start.await();
                return threadManager.tryMarkRunning(thread, AgentMode.ASK, AgentThreadStatus.IDLE);
            }));
        }
        start.countDown();

        long winners = 0;
        for (Future<Optional<AgentThread>> claim : claims) {
            if (claim.get().isPresent()) {
                winners++;
            }
        }
        pool.shutdownNow();

        // Then — exactly one attempt claimed the thread; the rest saw a turn already in flight
        assertThat(winners).isEqualTo(1);
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.RUNNING);
    }

    @Test
    void shouldRecordErrorResultAndContinueWhenToolThrows() {
        // Given — Ask mode; the model reads an execution that does not exist, so the READ tool throws
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c1", "read-execution", "missing"))));
        scriptedModel.enqueue(AiMessage.from("That execution does not exist."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "read execution missing", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the throw is a recoverable error result (not a turn failure); the loop continues to an answer
        assertThat(sink.error).isNull();
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT, AgentEvents.TOKEN, AgentEvents.DONE
        );
        assertThat(toolResultOutcome(sink)).isEqualTo("error");
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        // the streamed event carries the error message to the frontend (not just the outcome)
        assertThat(toolResult(sink).error()).contains("Execution not found");
        assertThat(toolResult(sink).reason()).isNull();
        // the error tool-result fed back to the model is flagged isError=true (not left null)
        assertThat(scriptedModel.lastRequestMessages())
            .filteredOn(ToolExecutionResultMessage.class::isInstance)
            .extracting(m -> ((ToolExecutionResultMessage) m).isError())
            .containsOnly(Boolean.TRUE);
        // the error result is durable and carries the tool author's message for the model to read
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .filteredOn(m -> m.type() == AgentMessageType.TOOL_RESULT)
            .allMatch(
                m -> "error".equals(m.toolResult().get("outcome"))
                    && String.valueOf(m.toolResult().get("error")).contains("Execution not found")
            );
    }

    @Test
    void shouldExecuteEveryToolWhenModelRequestsThemInParallel() {
        // Given — Ask mode; the model returns two read tool calls in one response (parallel tool calls)
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(
            AiMessage.from(
                "", List.of(
                    toolCall("c1", "read-execution-logs", "exec-1"),
                    toolCall("c2", "read-execution-logs", "exec-2")
                )
            )
        );
        scriptedModel.enqueue(AiMessage.from("Both runs failed on the load task."));
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "why did exec-1 and exec-2 fail?", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — BOTH calls run (neither is silently dropped), then the loop continues to a final answer
        assertThat(sink.names()).containsExactly(
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT,
            AgentEvents.TOOL_CALL, AgentEvents.TOOL_RESULT,
            AgentEvents.TOKEN, AgentEvents.DONE
        );
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .extracting(m -> m.role() + "/" + m.type())
            .containsExactly(
                "USER/TEXT",
                "ASSISTANT/TOOL_CALL", "TOOL/TOOL_RESULT",
                "ASSISTANT/TOOL_CALL", "TOOL/TOOL_RESULT",
                "ASSISTANT/TEXT"
            );
    }

    @Test
    void shouldNotCallModelWhenClientAlreadyDisconnected() {
        // Given — the client is already gone before the turn starts
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.enqueue(AiMessage.from("should not be consumed"));
        CollectingSink sink = new CollectingSink();
        sink.cancel();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "hi", AgentMode.ASK, TENANT, null, null, null), sink);

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
        orchestrator.runTurn(new AgentTurnContext(thread, "hi", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the streamed token was delivered, but the turn aborts before DONE (without erroring
        // or looping again) and the thread is returned to IDLE
        assertThat(sink.names()).contains(AgentEvents.TOKEN);
        assertThat(sink.names()).doesNotContain(AgentEvents.DONE);
        assertThat(sink.error).isNull();
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        assertThat(messageStore.load(thread.tenant(), thread.uid()))
            .anyMatch(m -> m.type() == AgentMessageType.CANCELLED);
    }

    @Test
    void shouldFailTurnWhenModelCallTimesOut() {
        // Given — the provider accepts the call but never responds
        AgentThread thread = newThread(AgentMode.ASK);
        scriptedModel.hang();
        CollectingSink sink = new CollectingSink();

        // When — the bounded model-call wait (PT1S in tests) elapses
        orchestrator.runTurn(new AgentTurnContext(thread, "hello?", AgentMode.ASK, TENANT, null, null, null), sink);

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
        orchestrator.runTurn(new AgentTurnContext(thread, "hello", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the turn fails cleanly: error surfaced and the thread is reset to IDLE
        assertThat(sink.error).isNotNull();
        assertThat(sink.completed).isFalse();
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldEndTurnGracefullyWhenSequentialToolCapIsReached() {
        // Given — Ask mode; the model calls a read tool every round (a runaway reasoning loop). Enqueue
        // one more tool-calling response than the default cap (25) so the cap is what stops the turn,
        // not an exhausted script.
        AgentThread thread = newThread(AgentMode.ASK);
        for (int i = 0; i <= 25; i++) {
            scriptedModel.enqueue(AiMessage.from("", List.of(toolCall("c" + i, "read-execution-logs", "exec-" + i))));
        }
        CollectingSink sink = new CollectingSink();

        // When
        orchestrator.runTurn(new AgentTurnContext(thread, "keep going forever", AgentMode.ASK, TENANT, null, null, null), sink);

        // Then — the turn ends gracefully (not failed) back to IDLE, after exactly the capped number of rounds
        assertThat(sink.error).isNull();
        assertThat(doneStatus(sink)).isEqualTo(AgentThreadStatus.IDLE.name());
        assertThat(reload(thread).status()).isEqualTo(AgentThreadStatus.IDLE);
        List<AgentMessage> log = messageStore.load(thread.tenant(), thread.uid());
        assertThat(log.stream().filter(m -> m.type() == AgentMessageType.TOOL_RESULT).count()).isEqualTo(25L);
        // the final assistant message explains the graceful stop
        assertThat(log.getLast().type()).isEqualTo(AgentMessageType.TEXT);
        assertThat(log.getLast().content()).contains("maximum number of tool steps");
    }

    private AgentThread newThread(final AgentMode mode) {
        return threadStore.create(
            AgentThread.builder()
                .uid(IdUtils.create())
                .tenant(TENANT)
                .mode(mode)
                .status(AgentThreadStatus.IDLE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deleted(false)
                .build()
        );
    }

    private AgentThread reload(final AgentThread thread) {
        return threadStore.find(TENANT, thread.uid()).orElseThrow();
    }

    /** Claim an awaiting thread for resumption, as the confirm endpoint does before calling resume. */
    private AgentThread claim(final AgentThread awaiting) {
        return threadManager.tryMarkRunning(awaiting, awaiting.mode(), AgentThreadStatus.AWAITING_CONFIRMATION).orElseThrow();
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
        return toolResult(sink).outcome();
    }

    private static AgentEvents.ToolResultEvent toolResult(final CollectingSink sink) {
        return (AgentEvents.ToolResultEvent) sink.first(AgentEvents.TOOL_RESULT);
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
        private final List<List<ChatMessage>> requestMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean hang;

        private void enqueue(final AiMessage message) {
            responses.addLast(message);
        }

        private void hang() {
            this.hang = true;
        }

        private void clear() {
            responses.clear();
            requestMessages.clear();
            this.hang = false;
        }

        /**
         * A snapshot of the messages the orchestrator sent on its most recent chat call. Snapshotted
         * because the orchestrator keeps mutating the same list as the loop appends responses.
         */
        private List<ChatMessage> lastRequestMessages() {
            if (requestMessages.isEmpty()) {
                throw new AssertionError("No chat request was sent to the model");
            }
            return requestMessages.get(requestMessages.size() - 1);
        }

        @Override
        public void chat(final ChatRequest request, final StreamingChatResponseHandler handler) {
            requestMessages.add(new ArrayList<>(request.messages()));
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
