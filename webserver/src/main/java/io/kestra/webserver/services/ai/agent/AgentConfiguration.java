package io.kestra.webserver.services.ai.agent;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

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
 * @param maxContextTurns the maximum number of most-recent turns replayed into the model context each
 *        turn; older turns stay persisted for history but are windowed out of the prompt. Windowing is
 *        by whole turns (grouped on {@code traceId}) so tool-call/result pairs are never split
 *        (default 10).
 */
@ConfigurationProperties("kestra.ai.agent")
public record AgentConfiguration(
    @Bindable(defaultValue = "PT5M") Duration modelCallTimeout,
    @Bindable(defaultValue = "https://api.kestra.io/v1/mcp") String docsMcpUrl,
    @Bindable(defaultValue = "25") int maxSequentialToolsInvocations,
    @Bindable(defaultValue = "50") int maxTurnsPerThread,
    @Bindable(defaultValue = "10") int maxContextTurns) {
}
