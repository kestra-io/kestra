package io.kestra.webserver.services.ai.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.data.message.*;
import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.internals.ChatMessageAdaptor;
import io.kestra.webserver.services.ai.agent.internals.TurnWindow;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog.ToolEntry;
import io.kestra.webserver.services.ai.agent.tool.ToolPermissionDeniedException;

import io.micronaut.core.annotation.Nullable;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The multi-turn control loop: assembles the mode profile, drives the streaming model, gates
 * confirmation-required tools, and suspends/resumes turns. Persistence, projection and thread-state
 * transitions are delegated to {@link AiThreadManager} and {@link ChatMessageAdaptor}.
 */
@Singleton
@Requires(bean = AiServiceManager.class)
@Slf4j
public class AgentOrchestrator {
    private final AiServiceManager aiServiceManager;
    private final ToolCatalog catalog;
    private final ModeProfiles modeProfiles;
    private final AiThreadManager threadManager;
    private final SystemPromptResolver systemPromptResolver;
    private final Duration modelCallTimeout;
    private final int maxContextTurns;
    private final int maxSequentialToolsInvocations;

    @Inject
    public AgentOrchestrator(
        final AiServiceManager aiServiceManager,
        final ToolCatalog catalog,
        final ModeProfiles modeProfiles,
        final AiThreadManager threadManager,
        final SystemPromptResolver systemPromptResolver,
        final AgentConfiguration configuration) {
        this.aiServiceManager = aiServiceManager;
        this.catalog = catalog;
        this.modeProfiles = modeProfiles;
        this.threadManager = threadManager;
        this.systemPromptResolver = systemPromptResolver;
        this.modelCallTimeout = configuration.modelCallTimeout();
        this.maxContextTurns = configuration.maxContextTurns();
        this.maxSequentialToolsInvocations = configuration.maxSequentialToolsInvocations();
    }

    public void runTurn(final AgentTurnContext context, final TurnEventSink sink) {
        AgentThread thread = context.thread();
        String traceId = thread.uid() + "-turn-" + (threadManager.load(thread.tenant(), thread.uid()).size() + 1);
        try {
            ResolvedProfile profile = modeProfiles.resolve(context.mode(), context.tenant(), context.principal());
            StreamingChatModel model = aiServiceManager.getAiService(context.providerId()).streamingChatModel(List.of());

            threadManager.appendUser(thread.tenant(), thread.uid(), traceId, context.prompt());

            List<ChatMessage> projected = ChatMessageAdaptor.project(TurnWindow.lastNTurns(threadManager.load(thread.tenant(), thread.uid()), maxContextTurns));
            List<ChatMessage> messages = new ArrayList<>(projected.size() + 2);
            String resolvedPrompt = systemPromptResolver.resolve(context.mode(), context.providerId(), profile.systemPrompt());
            messages.add(SystemMessage.from(withCommonSuffix(resolvedPrompt, profile.commonPrompt())));
            messages.addAll(projected);
            // Caller-supplied context for this turn only: appended at the end so the model sees it as the
            // latest input, but never persisted to the thread history (it is not appended via threadManager).
            additionalContextMessage(context.additionalContext()).ifPresent(messages::add);

            runLoop(
                new AgentLoopContext(
                    thread, context.tenant(), context.principal(), context.providerId(), context.mode(), profile, model, messages, traceId, new AtomicBoolean(false), new AtomicInteger(0)
                ), sink
            );
        } catch (Exception e) {
            failTurn(thread, sink, e);
        }
    }

    /**
     * Renders caller-supplied per-turn context into a trailing {@link UserMessage}. Serialized as JSON
     * so nested structures survive intact. Returns empty when there is no usable context (null, empty,
     * or unserializable) so nothing is added to the model input. Deliberately built here rather than
     * persisted via {@link AiThreadManager}, so it never enters the thread's durable history.
     *
     * @param additionalContext the caller-supplied context map for this turn, may be {@code null}
     * @return the context message to append at the end of the turn's input, or empty
     */
    /**
     * Appends the shared common prompt to the resolved (per-mode or custom) system prompt, so the shared
     * guidance applies to every prompt — including EE custom prompts, which otherwise replace the mode
     * persona entirely. A blank common prompt leaves the base unchanged.
     */
    private static String withCommonSuffix(final String base, final String commonPrompt) {
        return commonPrompt == null || commonPrompt.isBlank() ? base : base + "\n\n" + commonPrompt;
    }

    private Optional<ChatMessage> additionalContextMessage(final Map<String, Object> additionalContext) {
        if (additionalContext == null || additionalContext.isEmpty()) {
            return Optional.empty();
        }
        try {
            String json = JacksonMapper.ofJson().writeValueAsString(additionalContext);
            return Optional.of(UserMessage.from("Additional context for this request:\n" + json));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize additional context for a Copilot turn; continuing without it", e);
            return Optional.empty();
        }
    }

    /**
     * Resume a turn awaiting confirmation. The turn's state is reconstructed from the durable stores —
     * the message log (projected to the model-facing history and scanned for the pending proposed
     * action) and the thread (its mode) — with the provider supplied by the caller, exactly as a chat
     * turn is. So a resume can run on any node, not only the one that suspended the turn.
     */
    public void resume(final AgentThread running, final String providerId, final boolean approve, final String reason,
        final AgentPrincipal principal, final TurnEventSink sink) {
        try {
            List<AgentMessage> log = threadManager.load(running.tenant(), running.uid());
            AgentMessage pending = pendingActionOrThrow(log);
            // A confirmation resume is part of the same logical turn as the action it confirms: reuse that
            // turn's traceId so the resumed messages group with it. This keeps a turn = one trace, and
            // keeps every tool-call/result pair inside one turn so turn-windowing can never split them.
            String traceId = pending.traceId();
            ResolvedProfile profile = modeProfiles.resolve(running.mode(), running.tenant(), principal);
            StreamingChatModel model = aiServiceManager.getAiService(providerId).streamingChatModel(List.of());

            List<ChatMessage> projected = ChatMessageAdaptor.project(TurnWindow.lastNTurns(log, maxContextTurns));
            List<ChatMessage> messages = new ArrayList<>(projected.size() + 1);
            String resolvedPrompt = systemPromptResolver.resolve(running.mode(), providerId, profile.systemPrompt());
            messages.add(SystemMessage.from(withCommonSuffix(resolvedPrompt, profile.commonPrompt())));
            messages.addAll(projected);

            boolean planProposal = pending.toolCall() == null;
            // Preserve the per-turn sequential-tool-invocation count across the suspend/resume by seeding
            // it from the tool rounds already recorded in this turn.
            AgentLoopContext ctx = new AgentLoopContext(
                running, running.tenant(), principal, providerId, running.mode(), profile,
                model, messages, traceId, new AtomicBoolean(planProposal), new AtomicInteger(toolInvocationsInTurn(log, traceId))
            );

            if (planProposal) {
                resumePlan(ctx, approve, reason, sink);
            } else {
                resumeHeldAction(ctx, ChatMessageAdaptor.toRequest(pending.toolCall()), approve, reason, sink);
            }
        } catch (Exception e) {
            failTurn(running, sink, e);
        }
    }

    /** The last proposed action in the log — the plan or held tool call the confirmation applies to. */
    private static AgentMessage pendingActionOrThrow(final List<AgentMessage> log) {
        for (int i = log.size() - 1; i >= 0; i--) {
            if (log.get(i).type() == AgentMessageType.PROPOSED_ACTION) {
                return log.get(i);
            }
        }
        throw new ConflictException("There is no proposed action to resume in this thread.");
    }

    private void resumePlan(final AgentLoopContext ctx, final boolean approve, final String reason, final TurnEventSink sink) {
        if (!approve) {
            String summary = "Plan rejected%s. No actions were taken.".formatted(reason != null ? " (" + reason + ")" : "");
            threadManager.appendAssistantText(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), summary);
            finishTurn(ctx);
            done(sink, AgentThreadStatus.IDLE);
            return;
        }
        ctx.planApproved().set(true);
        String nudge = "The plan is approved. Carry it out now, one step at a time.";
        ctx.messages().add(UserMessage.from(nudge));
        threadManager.appendUser(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), nudge);
        runLoop(ctx, sink);
    }

    private void resumeHeldAction(final AgentLoopContext ctx, final ToolExecutionRequest held,
        final boolean approve, final String reason, final TurnEventSink sink) {
        ToolEntry entry = catalog.byName(held.name()).orElseThrow();

        if (!approve) {
            String rejectedText = "REJECTED by user." + (reason != null ? " Reason: " + reason : "");
            ctx.messages().add(ToolExecutionResultMessage.from(held, rejectedText));
            threadManager.appendToolResult(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(held, entry.kind(), entry.family()), rejectedResult(reason));
            emitToolResult(sink, held.name(), "rejected", null, reason);

            if (ctx.mode() == AgentMode.PLAN) {
                threadManager.appendAssistantText(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), "Action rejected; the plan has been aborted.");
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }
            runLoop(ctx, sink);
            return;
        }

        emitToolCall(sink, held, entry.kind(), entry.family());
        ToolCatalog.DispatchResult result;
        try {
            result = catalog.dispatch(held, callContext(ctx));
        } catch (ToolPermissionDeniedException e) {
            rejectTool(ctx, held, entry.kind(), entry.family(), e.getMessage(), sink);
            runLoop(ctx, sink);
            return;
        } catch (RuntimeException e) {
            failTool(ctx, held, entry.kind(), entry.family(), toolErrorMessage(e), sink);
            runLoop(ctx, sink);
            return;
        }
        if (result.artefact() != null) {
            publishArtefact(ctx, sink, result.artefact());
        }
        ctx.messages().add(ToolExecutionResultMessage.from(held, result.text()));
        threadManager.appendToolResult(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(held, entry.kind(), entry.family()), Map.of("outcome", "ok", "result", result.text()));
        emitToolResult(sink, held.name(), "ok", null, null);
        runLoop(ctx, sink);
    }

    private void runLoop(final AgentLoopContext ctx, final TurnEventSink sink) {
        while (true) {
            if (sink.isCancelled()) {
                abortCancelled(ctx);
                return;
            }

            ChatRequest request = ChatRequest.builder()
                .messages(ctx.messages())
                .toolSpecifications(ctx.profile().toolSpecifications())
                .build();

            ChatResponse response = callModel(ctx.model(), request, sink);

            if (sink.isCancelled()) {
                abortCancelled(ctx);
                return;
            }

            AiMessage ai = response.aiMessage();

            if (!ai.hasToolExecutionRequests()) {
                ctx.messages().add(ai);

                // An empty response (no text and no tool calls) — e.g. Gemini's intermittent empty
                // finishReason=STOP. Do NOT persist it: a blank assistant message would be replayed as an
                // empty model turn on the next turn (see ChatMessageAdaptor.project), which is itself a
                // documented trigger for more empty responses. End the turn without polluting the thread.
                if (ai.text() == null || ai.text().isBlank()) {
                    log.warn("Model returned an empty response (no text, no tool calls) for thread {}; ending turn without persisting.", ctx.thread().uid());
                    finishTurn(ctx);
                    done(sink, AgentThreadStatus.IDLE);
                    return;
                }

                if (ctx.mode() == AgentMode.PLAN && !ctx.planApproved().get()) {
                    suspendForPlan(ctx, ai.text(), sink);
                    return;
                }

                threadManager.appendAssistantText(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ai.text());
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }

            // The model requested tools: this is one sequential tool-calling round-trip. Guard against a
            // runaway reasoning loop — on exhaustion the turn is ended gracefully, not failed.
            log.info("Copilot thread {}: model requested {} tool call(s): {}", ctx.thread().uid(),
                ai.toolExecutionRequests().size(), ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList());
            if (ctx.toolInvocations().incrementAndGet() > maxSequentialToolsInvocations) {
                stopForToolBudget(ctx, sink);
                return;
            }

            ctx.messages().add(ai);
            ToolExecutionRequest heldAction = null;
            ToolEntry heldEntry = null;
            Map<String, Object> heldArgs = null;

            for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                Map<String, Object> args = ChatMessageAdaptor.parseArguments(req.arguments());

                if (!ctx.profile().allowedToolNames().contains(req.name())) {
                    rejectTool(ctx, req, AgentToolCall.Kind.PLATFORM, null, "Tool '%s' is not available in %s mode.".formatted(req.name(), ctx.mode()), sink);
                    continue;
                }

                ToolEntry entry = catalog.byName(req.name()).orElseThrow();
                threadManager.appendToolCall(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ai.text(),
                    ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family(), ChatMessageAdaptor.thinkingOf(ai)));

                if (entry.writePolicy() == AgentWritePolicy.CONFIRM) {
                    if (heldAction == null) {
                        heldAction = req;
                        heldEntry = entry;
                        heldArgs = args;
                    } else {
                        rejectTool(
                            ctx, req, entry.kind(), entry.family(),
                            "Only one action can be confirmed at a time; propose '%s' again on its own.".formatted(req.name()), sink
                        );
                    }
                    continue;
                }

                executeTool(ctx, req, entry, sink);
            }

            if (heldAction != null) {
                suspendForAction(ctx, heldAction, heldEntry, heldArgs, sink);
                return;
            }
        }
    }

    private void executeTool(final AgentLoopContext ctx, final ToolExecutionRequest req, final ToolEntry entry, final TurnEventSink sink) {
        log.info("Copilot thread {}: calling tool '{}' (kind={}, family={})", ctx.thread().uid(), req.name(), entry.kind(), entry.family());
        emitToolCall(sink, req, entry.kind(), entry.family());
        ToolCatalog.DispatchResult result;
        try {
            result = catalog.dispatch(req, callContext(ctx));
        } catch (ToolPermissionDeniedException e) {
            rejectTool(ctx, req, entry.kind(), entry.family(), e.getMessage(), sink);
            return;
        } catch (RuntimeException e) {
            failTool(ctx, req, entry.kind(), entry.family(), toolErrorMessage(e), sink);
            return;
        }
        if (result.artefact() != null) {
            publishArtefact(ctx, sink, result.artefact());
        }
        ctx.messages().add(ToolExecutionResultMessage.from(req, result.text()));
        threadManager.appendToolResult(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family()), Map.of("outcome", "ok", "result", result.text()));
        log.info("Copilot thread {}: tool '{}' returned ok ({} chars)", ctx.thread().uid(), req.name(), result.text() == null ? 0 : result.text().length());
        emitToolResult(sink, req.name(), "ok", null, null);
    }

    private void rejectTool(final AgentLoopContext ctx, final ToolExecutionRequest req, final AgentToolCall.Kind kind,
        final AgentToolFamily family, final String reason, final TurnEventSink sink) {
        log.info("Copilot thread {}: tool '{}' rejected: {}", ctx.thread().uid(), req.name(), reason);
        ctx.messages().add(ToolExecutionResultMessage.from(req, reason));
        threadManager.appendToolResult(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, kind, family), rejectedResult(reason));
        emitToolResult(sink, req.name(), "rejected", null, reason);
    }

    /**
     * A {@code @Tool} threw: surface the message to the model as an error tool-result so it can react
     * (retry, apologise, pick another tool) rather than aborting the whole turn. The failure is a
     * recoverable outcome, not a turn-level error.
     */
    private void failTool(final AgentLoopContext ctx, final ToolExecutionRequest req, final AgentToolCall.Kind kind,
        final AgentToolFamily family, final String message, final TurnEventSink sink) {
        log.warn("Copilot thread {}: tool '{}' failed: {}", ctx.thread().uid(), req.name(), message);
        // Mark the result as an error so the model (and provider APIs, e.g. Gemini's functionResponse)
        // see it as a failed tool call rather than a normal result — ToolExecutionResultMessage.from(..)
        // leaves isError null.
        ctx.messages().add(ToolExecutionResultMessage.builder()
            .id(req.id())
            .toolName(req.name())
            .text("Error: " + message)
            .isError(true)
            .build());
        threadManager.appendToolResult(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, kind, family), Map.of("outcome", "error", "error", message));
        emitToolResult(sink, req.name(), "error", message, null);
    }

    private void suspendForPlan(final AgentLoopContext ctx, final String planText, final TurnEventSink sink) {
        threadManager.appendProposedAction(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), planText, null);
        String confirmationId = IdUtils.create();
        threadManager.markAwaiting(ctx.thread(), confirmationId);
        sink.emit(AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(confirmationId, null, null, planText, null));
        done(sink, AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    private void suspendForAction(final AgentLoopContext ctx, final ToolExecutionRequest req,
        final ToolEntry entry, final Map<String, Object> args, final TurnEventSink sink) {
        threadManager.appendProposedAction(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), null, ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family()));
        String confirmationId = IdUtils.create();
        threadManager.markAwaiting(ctx.thread(), confirmationId);
        sink.emit(
            AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(
                confirmationId, req.name(), entry.family().name(), summaryFor(req.name(), args), args
            )
        );
        done(sink, AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    private ChatResponse callModel(final StreamingChatModel model, final ChatRequest request, final TurnEventSink sink) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(final String partial) {
                // The client may disconnect mid-stream; stop emitting tokens once the sink is cancelled.
                if (sink.isCancelled()) {
                    return;
                }
                if (partial != null && !partial.isEmpty()) {
                    sink.emit(AgentEvents.TOKEN, new AgentEvents.TokenEvent(partial));
                }
            }

            @Override
            public void onCompleteResponse(final ChatResponse complete) {
                future.complete(complete);
            }

            @Override
            public void onError(final Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {

            return future.get(modelCallTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("LLM streaming call timed out after " + modelCallTimeout, e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM streaming call interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("LLM streaming call failed: " + cause.getMessage(), cause);
        }
    }

    private void finishTurn(final AgentLoopContext ctx) {
        threadManager.finish(ctx.thread());
    }

    /**
     * Ends a turn gracefully after the sequential tool-invocation cap is hit: records an assistant note
     * (so the history explains the stop), streams it to the client, and returns the thread to IDLE — the
     * turn is stopped, not failed, so the user can simply ask it to continue.
     */
    private void stopForToolBudget(final AgentLoopContext ctx, final TurnEventSink sink) {
        log.warn("Copilot turn for thread {} hit the sequential tool-invocation cap ({})", ctx.thread().uid(), maxSequentialToolsInvocations);
        String message = "I reached the maximum number of tool steps (%d) for this turn and stopped before finishing. Ask me to continue if you'd like me to keep going."
            .formatted(maxSequentialToolsInvocations);
        threadManager.appendAssistantText(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), message);
        sink.emit(AgentEvents.TOKEN, new AgentEvents.TokenEvent(message));
        finishTurn(ctx);
        done(sink, AgentThreadStatus.IDLE);
    }

    private static int toolInvocationsInTurn(final List<AgentMessage> log, final String traceId) {
        return (int) log.stream()
            .filter(m -> m.type() == AgentMessageType.TOOL_CALL && traceId.equals(m.traceId()))
            .count();
    }

    private void abortCancelled(final AgentLoopContext ctx) {
        log.debug("Client disconnected; aborting turn for thread {}", ctx.thread().uid());
        threadManager.appendCancelled(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId());
        finishTurn(ctx);
    }

    private void failTurn(final AgentThread thread, final TurnEventSink sink, final Exception e) {
        log.error("Agent turn failed for thread {}", thread.uid(), e);
        try {
            threadManager.resetToIdleIfExists(thread.tenant(), thread.uid());
        } catch (Exception ignored) {
            // best-effort reset; the original failure is what we surface
        }
        sink.error(e);
    }

    private AgentCallContext.Context callContext(final AgentLoopContext ctx) {
        return new AgentCallContext.Context(ctx.tenant(), ctx.principal(), ctx.providerId(), ctx.thread().uid());
    }

    /** Persist and stream an artefact a tool produced — the counterpart of the tool's publishable result. */
    private void publishArtefact(final AgentLoopContext ctx, final TurnEventSink sink, final ArtefactDraft draft) {
        threadManager.appendArtefactDraft(ctx.thread().tenant(), ctx.thread().uid(), ctx.traceId(), draft);
        sink.emit(
            AgentEvents.ARTEFACT_DRAFT, new AgentEvents.ArtefactDraftEvent(
                draft.draftId(), draft.kind().name(), draft.yaml(), draft.valid(), draft.constraints()
            )
        );
    }

    private void emitToolCall(final TurnEventSink sink, final ToolExecutionRequest req, final AgentToolCall.Kind kind, final AgentToolFamily family) {
        sink.emit(
            AgentEvents.TOOL_CALL, new AgentEvents.ToolCallEvent(
                req.name(), kind.name(), family == null ? null : family.name(), ChatMessageAdaptor.parseArguments(req.arguments())
            )
        );
    }

    private void emitToolResult(final TurnEventSink sink, final String tool, final String outcome, @Nullable final String error, @Nullable final String reason) {
        sink.emit(AgentEvents.TOOL_RESULT, new AgentEvents.ToolResultEvent(tool, outcome, error, reason));
    }

    private void done(final TurnEventSink sink, final AgentThreadStatus status) {
        sink.emit(AgentEvents.DONE, new AgentEvents.DoneEvent(status.name()));
        sink.complete();
    }

    private static Map<String, Object> rejectedResult(final String reason) {
        return reason == null
            ? Map.of("outcome", "rejected")
            : Map.of("outcome", "rejected", "reason", reason);
    }

    /** The tool author's message, unwrapping langchain4j's {@link ToolExecutionException} envelope. */
    private static String toolErrorMessage(final RuntimeException e) {
        Throwable cause = e instanceof ToolExecutionException && e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }

    private String summaryFor(final String tool, final Map<String, Object> args) {
        return "Run `%s` with %s".formatted(tool, args);
    }
}
