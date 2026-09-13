package io.kestra.core.ai.agent.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micronaut.core.annotation.Nullable;

/**
 * Provider-agnostic snapshot of the model turn's reasoning state, persisted alongside a tool call so the
 * call can be replayed to the provider on a later turn with its reasoning intact. Reasoning-heavy models
 * reject tool-call history that drops this: Gemini and Anthropic both require the opaque
 * {@code signature} echoed back verbatim, and Anthropic additionally needs the {@code text} thinking block
 * (and any {@code redacted} blocks) to precede the tool use.
 * <p>
 * These map onto the three fields LangChain4j exposes on an {@code AiMessage} for every provider —
 * {@code thinking()} text plus the {@code thinking_signature} / {@code redacted_thinking} attributes — so a
 * single envelope covers all thinking-capable providers. All fields are null/empty for providers that emit
 * no reasoning state and for rows persisted before thinking was enabled.
 *
 * @param text      the thinking/reasoning text block, if any
 * @param signature the opaque provider reasoning token that must be echoed back verbatim, if any
 * @param redacted  encrypted/redacted thinking blocks the provider returned, if any
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentThinking(
    @Nullable String text,
    @Nullable String signature,
    @Nullable List<String> redacted) {

    public boolean isEmpty() {
        return (text == null || text.isBlank())
            && (signature == null || signature.isBlank())
            && (redacted == null || redacted.isEmpty());
    }
}
