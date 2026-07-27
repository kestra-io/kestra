package io.kestra.webserver.services.ai.agent;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import lombok.Builder;

/**
 * Configuration for the AI Copilot agent, under {@code kestra.ai.agent}.
 *
 * @param modelCallTimeout how long a single streaming model call may run before the turn is failed,
 *        so a hung provider cannot pin an executor thread (default 5 minutes).
 * @param docsMcpUrl the Kestra docs MCP endpoint used for Ask-mode grounding.
 * @param maxSequentialToolsInvocations the maximum number of sequential tool-calling round-trips
 *        (model responses that request tools) within a single turn, mirroring langchain4j's setting
 *        of the same name. Bounds a runaway reasoning loop; counted per turn and preserved across a
 *        confirmation suspend/resume. On exhaustion the turn is ended gracefully, not failed
 *        (default 25 — deliberately below langchain4j's 100, as each round-trip is a paid model call).
 * @param maxTurnsPerThread the maximum number of user turns a single conversation thread may hold
 *        before new turns are refused, as a cost/abuse guardrail (default 50).
 * @param maxConcurrentTurns the per-node ceiling on simultaneously-running agent turns; once reached,
 *        new turns are rejected with 429 rather than queued. A cost/provider-load guardrail: turns run
 *        on virtual threads, so the thread count is a non-issue and this knob purely bounds concurrent
 *        provider load (default 32).
 * @param maxContextTurns the maximum number of most-recent turns replayed into the model context each
 *        turn; older turns stay persisted for history but are windowed out of the prompt. Windowing is
 *        by whole turns (grouped on {@code traceId}) so tool-call/result pairs are never split
 *        (default 10).
 * @param inMemoryConversationTtl for the in-memory (non-durable) Copilot store only: how long a
 *        conversation is retained after its last activity before it is evicted; the store keeps no
 *        history, so a conversation is dropped once it has been idle this long (default 1 hour).
 *        Ignored when a durable backend is in use.
 * @param maxInMemoryConversations for the in-memory store only: the hard cap on retained
 *        conversations; once exceeded the least-recently-active conversation is evicted, bounding
 *        memory. A safety ceiling above the idle-TTL sweep — a single account keeps only a handful of
 *        live conversations (default 50). Ignored when a durable backend is in use.
 */
@ConfigurationProperties("kestra.ai.agent")
@Builder
public record AgentConfiguration(
    @Bindable(defaultValue = "PT5M") Duration modelCallTimeout,
    @Bindable(defaultValue = "https://api.kestra.io/v1/mcp") String docsMcpUrl,
    @Bindable(defaultValue = "25") int maxSequentialToolsInvocations,
    @Bindable(defaultValue = "50") int maxTurnsPerThread,
    @Bindable(defaultValue = "32") int maxConcurrentTurns,
    @Bindable(defaultValue = "10") int maxContextTurns,
    @Bindable(defaultValue = "PT1H") Duration inMemoryConversationTtl,
    @Bindable(defaultValue = "50") int maxInMemoryConversations) {
}
