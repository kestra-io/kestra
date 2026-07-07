package io.kestra.webserver.services.ai.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.internals.ChatMessageAdaptor;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog.ToolEntry;

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
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The multi-turn control loop: assembles the mode profile, drives the streaming model, gates
 * confirmation-required tools, and suspends/resumes turns. Persistence, projection and thread-state
 * transitions are delegated to {@link ConversationLog}, {@link ChatMessageAdaptor} and
 * {@link ThreadLifecycle}.
 */
@Singleton
@Requires(bean = AiServiceManager.class)
@Slf4j
public class AgentOrchestrator {
    private final AiServiceManager aiServiceManager;
    private final ToolCatalog catalog;
    private final ModeProfiles modeProfiles;
    private final ThreadLifecycle lifecycle;
    private final ConversationLog conversation;
    private final ConfirmationRegistry confirmationRegistry;
    private final Duration modelCallTimeout;

    @Inject
    public AgentOrchestrator(
        final AiServiceManager aiServiceManager,
        final ToolCatalog catalog,
        final ModeProfiles modeProfiles,
        final ThreadLifecycle lifecycle,
        final ConversationLog conversation,
        final ConfirmationRegistry confirmationRegistry,
        final AgentConfiguration configuration
    ) {
        this.aiServiceManager = aiServiceManager;
        this.catalog = catalog;
        this.modeProfiles = modeProfiles;
        this.lifecycle = lifecycle;
        this.conversation = conversation;
        this.confirmationRegistry = confirmationRegistry;
        this.modelCallTimeout = configuration.modelCallTimeout();
    }

    private static final class LoopContext {
        private AgentThread thread;
        private final String tenant;
        private final String providerId;
        private final AgentMode mode;
        private final ResolvedProfile profile;
        private final StreamingChatModel model;
        private final List<ChatMessage> messages;
        private final String traceId;
        private boolean planApproved;

        private LoopContext(AgentThread thread, String tenant, String providerId, AgentMode mode,
                            ResolvedProfile profile, StreamingChatModel model, List<ChatMessage> messages,
                            String traceId, boolean planApproved) {
            this.thread = thread;
            this.tenant = tenant;
            this.providerId = providerId;
            this.mode = mode;
            this.profile = profile;
            this.model = model;
            this.messages = messages;
            this.traceId = traceId;
            this.planApproved = planApproved;
        }
    }

    public void runTurn(final AgentThread thread, final String prompt, final AgentMode mode,
                        final String tenant, final String providerId, final TurnEventSink sink) {
        String traceId = thread.uid() + "-turn-" + (conversation.load(thread.uid()).size() + 1);
        try {
            ResolvedProfile profile = modeProfiles.resolve(mode);
            StreamingChatModel model = aiServiceManager.getAiService(providerId).streamingChatModel(List.of());

            AgentThread running = lifecycle.markRunning(thread, mode);
            conversation.appendUser(running.uid(), traceId, prompt);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(profile.systemPrompt()));
            messages.addAll(ChatMessageAdaptor.project(conversation.load(running.uid())));

            runLoop(new LoopContext(running, tenant, providerId, mode, profile, model, messages, traceId, false), sink);
        } catch (Exception e) {
            failTurn(thread, sink, e);
        }
    }

    public void resume(final SuspendedTurn turn, final boolean approve, final String reason, final TurnEventSink sink) {
        AgentThread thread = lifecycle.find(turn.tenant(), turn.threadId()).orElseThrow();
        try {
            StreamingChatModel model = aiServiceManager.getAiService(turn.providerId()).streamingChatModel(List.of());
            AgentThread running = lifecycle.markRunning(thread, turn.mode());
            LoopContext ctx = new LoopContext(
                running, turn.tenant(), turn.providerId(), turn.mode(), turn.profile(),
                model, turn.messages(), turn.traceId(), turn.planProposal()
            );

            if (turn.planProposal()) {
                resumePlan(ctx, approve, reason, sink);
            } else {
                resumeHeldAction(ctx, turn.heldRequest(), approve, reason, sink);
            }
        } catch (Exception e) {
            failTurn(thread, sink, e);
        }
    }

    private void resumePlan(final LoopContext ctx, final boolean approve, final String reason, final TurnEventSink sink) {
        if (!approve) {
            String summary = "Plan rejected" + (reason != null ? " (" + reason + ")" : "") + ". No actions were taken.";
            conversation.appendAssistantText(ctx.thread.uid(), ctx.traceId, summary);
            finishTurn(ctx);
            done(sink, AgentThreadStatus.IDLE);
            return;
        }
        ctx.planApproved = true;
        String nudge = "The plan is approved. Carry it out now, one step at a time.";
        ctx.messages.add(UserMessage.from(nudge));
        conversation.appendUser(ctx.thread.uid(), ctx.traceId, nudge);
        runLoop(ctx, sink);
    }

    private void resumeHeldAction(final LoopContext ctx, final ToolExecutionRequest held,
                                  final boolean approve, final String reason, final TurnEventSink sink) {
        ToolEntry entry = catalog.byName(held.name()).orElseThrow();

        if (!approve) {
            String rejectedText = "REJECTED by user." + (reason != null ? " Reason: " + reason : "");
            ctx.messages.add(ToolExecutionResultMessage.from(held, rejectedText));
            conversation.appendToolResult(ctx.thread.uid(), ctx.traceId, ChatMessageAdaptor.toToolCall(held, entry.family()), rejectedResult(reason));
            emitToolResult(sink, held.name(), "rejected");

            if (ctx.mode == AgentMode.PLAN) {
                conversation.appendAssistantText(ctx.thread.uid(), ctx.traceId, "Action rejected; the plan has been aborted.");
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }
            runLoop(ctx, sink);
            return;
        }

        emitToolCall(sink, held, entry.family());
        String result = catalog.dispatch(held, ctx.tenant);
        ctx.messages.add(ToolExecutionResultMessage.from(held, result));
        conversation.appendToolResult(ctx.thread.uid(), ctx.traceId, ChatMessageAdaptor.toToolCall(held, entry.family()), Map.of("outcome", "ok", "result", result));
        emitToolResult(sink, held.name(), "ok");
        runLoop(ctx, sink);
    }

    private void runLoop(final LoopContext ctx, final TurnEventSink sink) {
        while (true) {
            if (sink.isCancelled()) {
                abortCancelled(ctx);
                return;
            }

            ChatRequest request = ChatRequest.builder()
                .messages(ctx.messages)
                .toolSpecifications(ctx.profile.toolSpecifications())
                .build();

            ChatResponse response = callModel(ctx.model, request, sink);

            if (sink.isCancelled()) {
                abortCancelled(ctx);
                return;
            }

            AiMessage ai = response.aiMessage();

            if (!ai.hasToolExecutionRequests()) {
                ctx.messages.add(ai);

                if (ctx.mode == AgentMode.PLAN && !ctx.planApproved) {
                    suspendForPlan(ctx, ai.text(), sink);
                    return;
                }

                conversation.appendAssistantText(ctx.thread.uid(), ctx.traceId, ai.text());
                finishTurn(ctx);
                done(sink, AgentThreadStatus.IDLE);
                return;
            }

            ToolExecutionRequest req = ai.toolExecutionRequests().getFirst();
            ctx.messages.add(AiMessage.from(ai.text(), List.of(req)));
            Map<String, Object> args = ChatMessageAdaptor.parseArguments(req.arguments());

            if (!ctx.profile.allowedToolNames().contains(req.name())) {
                String rejected = "Tool '" + req.name() + "' is not available in " + ctx.mode + " mode.";
                ctx.messages.add(ToolExecutionResultMessage.from(req, rejected));
                conversation.appendToolResult(ctx.thread.uid(), ctx.traceId, ChatMessageAdaptor.toToolCall(req, null), rejectedResult(rejected));
                emitToolResult(sink, req.name(), "rejected");
                continue;
            }

            ToolEntry entry = catalog.byName(req.name()).orElseThrow();
            conversation.appendToolCall(ctx.thread.uid(), ctx.traceId, ai.text(), ChatMessageAdaptor.toToolCall(req, entry.family()));

            if (entry.writePolicy() == AgentWritePolicy.CONFIRM) {
                suspendForAction(ctx, req, entry, args, sink);
                return;
            }

            emitToolCall(sink, req, entry.family());
            String result = catalog.dispatch(req, ctx.tenant);
            ctx.messages.add(ToolExecutionResultMessage.from(req, result));
            conversation.appendToolResult(ctx.thread.uid(), ctx.traceId, ChatMessageAdaptor.toToolCall(req, entry.family()), Map.of("outcome", "ok", "result", result));
            emitToolResult(sink, req.name(), "ok");
        }
    }

    private void suspendForPlan(final LoopContext ctx, final String planText, final TurnEventSink sink) {
        String confirmationId = IdUtils.create();
        conversation.appendProposedAction(ctx.thread.uid(), ctx.traceId, planText, null);
        ctx.thread = lifecycle.markAwaiting(ctx.thread);
        confirmationRegistry.park(SuspendedTurn.builder()
            .confirmationId(confirmationId)
            .threadId(ctx.thread.uid())
            .tenant(ctx.tenant)
            .providerId(ctx.providerId)
            .mode(ctx.mode)
            .profile(ctx.profile)
            .messages(ctx.messages)
            .traceId(ctx.traceId)
            .planProposal(true)
            .heldRequest(null)
            .build());
        sink.emit(AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(confirmationId, null, null, planText, null));
        done(sink, AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    private void suspendForAction(final LoopContext ctx, final ToolExecutionRequest req,
                                  final ToolEntry entry, final Map<String, Object> args, final TurnEventSink sink) {
        String confirmationId = IdUtils.create();
        conversation.appendProposedAction(ctx.thread.uid(), ctx.traceId, null, ChatMessageAdaptor.toToolCall(req, entry.family()));
        ctx.thread = lifecycle.markAwaiting(ctx.thread);
        confirmationRegistry.park(SuspendedTurn.builder()
            .confirmationId(confirmationId)
            .threadId(ctx.thread.uid())
            .tenant(ctx.tenant)
            .providerId(ctx.providerId)
            .mode(ctx.mode)
            .profile(ctx.profile)
            .messages(ctx.messages)
            .traceId(ctx.traceId)
            .planProposal(false)
            .heldRequest(req)
            .build());
        sink.emit(AgentEvents.PROPOSED_ACTION, new AgentEvents.ProposedActionEvent(
            confirmationId, req.name(), entry.family().name(), summaryFor(req.name(), args), args
        ));
        done(sink, AgentThreadStatus.AWAITING_CONFIRMATION);
    }

    private ChatResponse callModel(final StreamingChatModel model, final ChatRequest request, final TurnEventSink sink) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(final String partial) {
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

    private void finishTurn(final LoopContext ctx) {
        ctx.thread = lifecycle.finish(ctx.thread, conversation.deriveTitle(ctx.thread.uid()));
    }

    private void abortCancelled(final LoopContext ctx) {
        log.debug("Client disconnected; aborting turn for thread {}", ctx.thread.uid());
        finishTurn(ctx);
    }

    private void failTurn(final AgentThread thread, final TurnEventSink sink, final Exception e) {
        log.error("Agent turn failed for thread {}", thread.uid(), e);
        try {
            lifecycle.resetToIdle(thread);
        } catch (Exception ignored) {
            // best-effort reset; the original failure is what we surface
        }
        sink.error(e);
    }

    private void emitToolCall(final TurnEventSink sink, final ToolExecutionRequest req, final AgentToolFamily family) {
        sink.emit(AgentEvents.TOOL_CALL, new AgentEvents.ToolCallEvent(
            req.name(), family == null ? null : family.name(), ChatMessageAdaptor.parseArguments(req.arguments())
        ));
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

    private String summaryFor(final String tool, final Map<String, Object> args) {
        return "Run `" + tool + "` with " + args;
    }
}
