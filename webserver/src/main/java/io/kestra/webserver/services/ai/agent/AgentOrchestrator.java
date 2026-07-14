package io.kestra.webserver.services.ai.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.domain.AgentToolCall;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;
import io.kestra.webserver.services.ai.agent.internals.ChatMessageAdaptor;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog.ToolEntry;
import io.kestra.webserver.services.ai.agent.tool.ToolPermissionDeniedException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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
    private final ConfirmationRegistry confirmationRegistry;
    private final Duration modelCallTimeout;

    @Inject
    public AgentOrchestrator(
        final AiServiceManager aiServiceManager,
        final ToolCatalog catalog,
        final ModeProfiles modeProfiles,
        final AiThreadManager threadManager,
        final ConfirmationRegistry confirmationRegistry,
        final AgentConfiguration configuration) {
        this.aiServiceManager = aiServiceManager;
        this.catalog = catalog;
        this.modeProfiles = modeProfiles;
        this.threadManager = threadManager;
        this.confirmationRegistry = confirmationRegistry;
        this.modelCallTimeout = configuration.modelCallTimeout();
    }

    public void runTurn(final AgentTurnContext context, final TurnEventSink sink) {
        AgentThread thread = context.thread();
        String traceId = thread.uid() + "-turn-" + (threadManager.load(thread.uid()).size() + 1);
        try {
            ResolvedProfile profile = modeProfiles.resolve(context.mode(), context.tenant(), context.principal());
            StreamingChatModel model = aiServiceManager.getAiService(context.providerId()).streamingChatModel(List.of());

            threadManager.appendUser(thread.uid(), traceId, context.prompt());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(profile.systemPrompt()));
            messages.addAll(ChatMessageAdaptor.project(threadManager.load(thread.uid())));

            runLoop(
                new AgentLoopContext(thread, context.tenant(), context.principal(), context.providerId(), context.mode(), profile, model, messages, traceId, new AtomicBoolean(false)), sink
            );
        } catch (Exception e) {
            failTurn(thread, sink, e);
        }
    }

    public void resume(final SuspendedTurn turn, final AgentThread running, final boolean approve, final String reason,
        final AgentPrincipal principal, final TurnEventSink sink) {
        try {
            StreamingChatModel model = aiServiceManager.getAiService(turn.providerId()).streamingChatModel(List.of());
            AgentLoopContext ctx = new AgentLoopContext(
                running, turn.tenant(), principal, turn.providerId(), turn.mode(), turn.profile(),
                model, turn.messages(), turn.traceId(), new AtomicBoolean(turn.planProposal())
            );

            if (turn.planProposal()) {
                resumePlan(ctx, approve, reason, sink);
            } else {
                resumeHeldAction(ctx, turn.heldRequest(), approve, reason, sink);
            }
        } catch (Exception e) {
            failTurn(running, sink, e);
        }
    }

    private void resumePlan(final AgentLoopContext ctx, final boolean approve, final String reason, final TurnEventSink sink) {
        if (!approve) {
            String summary = "Plan rejected" + (reason != null ? " (" + reason + ")" : "") + ". No actions were taken.";
            threadManager.appendAssistantText(ctx.thread().uid(), ctx.traceId(), summary);
            finishTurn(ctx);
            done(sink, AgentThreadStatus.IDLE);
            return;
        }
        ctx.planApproved().set(true);
        String nudge = "The plan is approved. Carry it out now, one step at a time.";
        ctx.messages().add(UserMessage.from(nudge));
        threadManager.appendUser(ctx.thread().uid(), ctx.traceId(), nudge);
        runLoop(ctx, sink);
    }

    private void resumeHeldAction(final AgentLoopContext ctx, final ToolExecutionRequest held,
        final boolean approve, final String reason, final TurnEventSink sink) {
        ToolEntry entry = catalog.byName(held.name()).orElseThrow();

        if (!approve) {
            String rejectedText = "REJECTED by user." + (reason != null ? " Reason: " + reason : "");
            ctx.messages().add(ToolExecutionResultMessage.from(held, rejectedText));
            threadManager.appendToolResult(ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(held, entry.kind(), entry.family()), rejectedResult(reason));
            emitToolResult(sink, held.name(), "rejected");

            if (ctx.mode() == AgentMode.PLAN) {
                threadManager.appendAssistantText(ctx.thread().uid(), ctx.traceId(), "Action rejected; the plan has been aborted.");
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }
            runLoop(ctx, sink);
            return;
        }

        emitToolCall(sink, held, entry.kind(), entry.family());
        String result;
        try {
            result = catalog.dispatch(held, callContext(ctx, sink));
        } catch (ToolPermissionDeniedException e) {
            rejectTool(ctx, held, entry.kind(), entry.family(), e.getMessage(), sink);
            runLoop(ctx, sink);
            return;
        } catch (RuntimeException e) {
            failTool(ctx, held, entry.kind(), entry.family(), toolErrorMessage(e), sink);
            runLoop(ctx, sink);
            return;
        }
        ctx.messages().add(ToolExecutionResultMessage.from(held, result));
        threadManager.appendToolResult(ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(held, entry.kind(), entry.family()), Map.of("outcome", "ok", "result", result));
        emitToolResult(sink, held.name(), "ok");
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

                if (ctx.mode() == AgentMode.PLAN && !ctx.planApproved().get()) {
                    suspendForPlan(ctx, ai.text(), sink);
                    return;
                }

                threadManager.appendAssistantText(ctx.thread().uid(), ctx.traceId(), ai.text());
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }

            ctx.messages().add(ai);
            ToolExecutionRequest heldAction = null;
            ToolEntry heldEntry = null;
            Map<String, Object> heldArgs = null;

            for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                Map<String, Object> args = ChatMessageAdaptor.parseArguments(req.arguments());

                if (!ctx.profile().allowedToolNames().contains(req.name())) {
                    rejectTool(ctx, req, AgentToolCall.Kind.PLATFORM, null, "Tool '" + req.name() + "' is not available in " + ctx.mode() + " mode.", sink);
                    continue;
                }

                ToolEntry entry = catalog.byName(req.name()).orElseThrow();
                threadManager.appendToolCall(ctx.thread().uid(), ctx.traceId(), ai.text(), ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family()));

                if (entry.writePolicy() == AgentWritePolicy.CONFIRM) {
                    if (heldAction == null) {
                        heldAction = req;
                        heldEntry = entry;
                        heldArgs = args;
                    } else {
                        rejectTool(
                            ctx, req, entry.kind(), entry.family(),
                            "Only one action can be confirmed at a time; propose '" + req.name() + "' again on its own.", sink
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
        emitToolCall(sink, req, entry.kind(), entry.family());
        String result;
        try {
            result = catalog.dispatch(req, callContext(ctx, sink));
        } catch (ToolPermissionDeniedException e) {
            rejectTool(ctx, req, entry.kind(), entry.family(), e.getMessage(), sink);
            return;
        } catch (RuntimeException e) {
            failTool(ctx, req, entry.kind(), entry.family(), toolErrorMessage(e), sink);
            return;
        }
        ctx.messages().add(ToolExecutionResultMessage.from(req, result));
        threadManager.appendToolResult(ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family()), Map.of("outcome", "ok", "result", result));
        emitToolResult(sink, req.name(), "ok");
    }

    private void rejectTool(final AgentLoopContext ctx, final ToolExecutionRequest req, final AgentToolCall.Kind kind,
        final AgentToolFamily family, final String reason, final TurnEventSink sink) {
        ctx.messages().add(ToolExecutionResultMessage.from(req, reason));
        threadManager.appendToolResult(ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, kind, family), rejectedResult(reason));
        emitToolResult(sink, req.name(), "rejected");
    }

    /**
     * A {@code @Tool} threw: surface the message to the model as an error tool-result so it can react
     * (retry, apologise, pick another tool) rather than aborting the whole turn. The failure is a
     * recoverable outcome, not a turn-level error.
     */
    private void failTool(final AgentLoopContext ctx, final ToolExecutionRequest req, final AgentToolCall.Kind kind,
        final AgentToolFamily family, final String message, final TurnEventSink sink) {
        ctx.messages().add(ToolExecutionResultMessage.from(req, "Error: " + message));
        threadManager.appendToolResult(ctx.thread().uid(), ctx.traceId(), ChatMessageAdaptor.toToolCall(req, kind, family), Map.of("outcome", "error", "error", message));
        emitToolResult(sink, req.name(), "error");
    }

    private void suspendForPlan(final AgentLoopContext ctx, final String planText, final TurnEventSink sink) {
        threadManager.appendProposedAction(ctx.thread().uid(), ctx.traceId(), planText, null);
        threadManager.markAwaiting(ctx.thread());
        SuspendedTurn turn = SuspendedTurn.forPlan(ctx);
        confirmationRegistry.park(turn);
        sink.emit(AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(turn.confirmationId(), null, null, planText, null));
        done(sink, AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    private void suspendForAction(final AgentLoopContext ctx, final ToolExecutionRequest req,
        final ToolEntry entry, final Map<String, Object> args, final TurnEventSink sink) {
        threadManager.appendProposedAction(ctx.thread().uid(), ctx.traceId(), null, ChatMessageAdaptor.toToolCall(req, entry.kind(), entry.family()));
        threadManager.markAwaiting(ctx.thread());
        SuspendedTurn turn = SuspendedTurn.forAction(ctx, req);
        confirmationRegistry.park(turn);
        sink.emit(
            AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(
                turn.confirmationId(), req.name(), entry.family().name(), summaryFor(req.name(), args), args
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

    private void abortCancelled(final AgentLoopContext ctx) {
        log.debug("Client disconnected; aborting turn for thread {}", ctx.thread().uid());
        threadManager.appendCancelled(ctx.thread().uid(), ctx.traceId());
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

    private AgentCallContext.Context callContext(final AgentLoopContext ctx, final TurnEventSink sink) {
        return new AgentCallContext.Context(ctx.tenant(), ctx.principal(), ctx.providerId(), ctx.thread().uid(), draft ->
        {
            threadManager.appendArtefactDraft(ctx.thread().uid(), ctx.traceId(), draft);
            sink.emit(
                AgentEvents.ARTEFACT_DRAFT, new AgentEvents.ArtefactDraftEvent(
                    draft.draftId(), draft.kind().name(), draft.yaml(), draft.valid(), draft.constraints()
                )
            );
        });
    }

    private void emitToolCall(final TurnEventSink sink, final ToolExecutionRequest req, final AgentToolCall.Kind kind, final AgentToolFamily family) {
        sink.emit(
            AgentEvents.TOOL_CALL, new AgentEvents.ToolCallEvent(
                req.name(), kind.name(), family == null ? null : family.name(), ChatMessageAdaptor.parseArguments(req.arguments())
            )
        );
    }

    private void emitToolResult(final TurnEventSink sink, final String tool, final String outcome) {
        sink.emit(AgentEvents.TOOL_RESULT, new AgentEvents.ToolResultEvent(tool, outcome));
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
        return "Run `" + tool + "` with " + args;
    }
}
