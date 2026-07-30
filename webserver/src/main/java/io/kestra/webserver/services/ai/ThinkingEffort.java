package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * Provider-neutral reasoning effort/level for providers configured by effort rather than a token budget.
 * Each {@code AiService} maps it to its provider's own vocabulary (OpenAI {@code reasoning_effort},
 * Gemini {@code thinkingLevel}).
 */
public enum ThinkingEffort {
    LOW,
    MEDIUM,
    HIGH;

    /** Case-insensitive lookup that fails fast on an unknown value (no silent fallback). */
    @JsonCreator
    public static ThinkingEffort fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, ThinkingEffort.class);
    }

    /** The lowercase token providers expect (e.g. {@code "low"}). */
    public String value() {
        return name().toLowerCase();
    }
}
