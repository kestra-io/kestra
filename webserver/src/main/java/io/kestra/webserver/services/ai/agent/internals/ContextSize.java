package io.kestra.webserver.services.ai.agent.internals;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.serializers.JacksonMapper;

/**
 * Measures how much conversation a turn would send to the model, in characters. Stateless.
 * <p>
 * Characters rather than tokens: tokenization is provider-specific, so an exact token count would need a
 * per-provider tokenizer and would still change under the caller's feet when the provider is switched
 * mid-thread. A character count is deterministic, cheap, and close enough for a guardrail — roughly four
 * characters per token.
 * <p>
 * Measured over the same durable rows the prompt is built from, so the caller must window the history
 * with {@link TurnWindow#lastNTurns} first: a long thread only sends its trailing turns, and the cost
 * being guarded is the cost of the <em>next</em> request, not of the whole stored conversation. The
 * system prompt is excluded — it is fixed per mode and not something a conversation can grow.
 */
public final class ContextSize {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private ContextSize() {
    }

    /**
     * The size, in characters, of the given message rows once projected into the model context: message
     * text plus the serialized tool-call arguments and tool results, which are what actually grow.
     *
     * @param rows the (already windowed) durable message log, oldest-first.
     * @return the total character count.
     */
    public static long charsOf(final List<AgentMessage> rows) {
        long total = 0;
        for (AgentMessage row : rows) {
            if (row.content() != null) {
                total += row.content().length();
            }
            if (row.toolCall() != null) {
                total += jsonLength(row.toolCall().arguments());
            }
            if (row.toolResult() != null) {
                total += jsonLength(row.toolResult());
            }
        }
        return total;
    }

    /**
     * Serialized length of a value, or 0 when it cannot be serialized. An unserializable value is not
     * sent to the model either, so treating it as weightless keeps the estimate consistent with the
     * projection rather than failing the guard on it.
     */
    private static int jsonLength(final Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return MAPPER.writeValueAsString(value).length();
        } catch (Exception e) {
            return 0;
        }
    }
}
