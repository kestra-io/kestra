package io.kestra.webserver.services.ai.agent.internals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.ai.agent.models.AgentMessage;

/**
 * A stateless per-turn context window over the durable {@link AgentMessage} history — the guardrail that
 * keeps a long thread within a bounded prompt size before it is sent to the model. It keeps only the
 * most-recent turns and drops older ones; the older messages stay persisted for history, they are just
 * windowed out of the model context.
 * <p>
 * Windowing is done on the raw {@code AgentMessage} log <em>before</em> projection, by whole turns
 * grouped on {@link AgentMessage#traceId()}. Because a turn's messages share one {@code traceId} (a
 * confirmation resume reuses the suspended turn's {@code traceId}), a tool call and its result always
 * live in the same group — so windowing by turns can never split a tool-call/result pair, and no
 * orphaned tool result is ever produced. The orchestrator prepends the (pinned) system prompt itself,
 * so it is never subject to windowing.
 */
public final class TurnWindow {
    private TurnWindow() {
    }

    /**
     * Returns the messages belonging to the most-recent {@code maxTurns} turns, oldest-first.
     *
     * @param history the full durable message log, oldest-first.
     * @param maxTurns the maximum number of trailing turns to keep; a value {@code <= 0} disables
     *        windowing and returns the input unchanged.
     * @return the windowed history, oldest-first.
     */
    public static List<AgentMessage> lastNTurns(final List<AgentMessage> history, final int maxTurns) {
        if (maxTurns <= 0 || history.isEmpty()) {
            return history;
        }

        // Distinct turns in encounter order — a turn's messages are contiguous, sharing one traceId.
        LinkedHashSet<String> turns = new LinkedHashSet<>();
        for (AgentMessage message : history) {
            turns.add(message.traceId());
        }
        if (turns.size() <= maxTurns) {
            return history;
        }

        // Keep only the trailing maxTurns traceIds.
        Set<String> kept = turns.stream().skip(turns.size() - (long) maxTurns).collect(Collectors.toSet());
        return history.stream().filter(message -> kept.contains(message.traceId())).toList();
    }
}
