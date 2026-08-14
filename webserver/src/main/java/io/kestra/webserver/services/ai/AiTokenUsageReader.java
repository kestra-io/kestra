package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Reads the token counts of one model call off whatever {@link TokenUsage} the provider returned.
 *
 * <p>langchain4j reports the two counts that matter most for cost — the cached share of the prompt, and
 * thinking tokens — only on provider-specific subclasses. This reads everything the common type offers, plus
 * the Gemini subclass it can see.
 *
 * <p>A bean rather than a static helper so a distribution with other provider libraries on its classpath can
 * replace it. Without that, those providers report a prompt total with no cached share and the whole prompt
 * weighs at the cold rate — an overestimate, which is the safe direction for a ceiling to err in.
 */
@Singleton
public class AiTokenUsageReader {
    /**
     * The counts for a single call, all zero when the provider reported none — which is not an error: a turn
     * that fails mid-stream reports nothing, and should record nothing rather than fail.
     */
    public AiUsageTotals read(@Nullable TokenUsage usage) {
        if (usage == null) {
            return AiUsageTotals.ZERO;
        }

        long prompt = count(usage.inputTokenCount());
        // Gemini's outputTokenCount is candidatesTokenCount, which excludes thoughts — so the two add rather
        // than overlap.
        long completion = count(usage.outputTokenCount());

        if (usage instanceof GoogleAiGeminiTokenUsage gemini) {
            return new AiUsageTotals(
                prompt,
                count(gemini.cachedContentTokenCount()),
                completion,
                count(gemini.thoughtsTokenCount())
            );
        }

        return new AiUsageTotals(prompt, 0, completion, 0);
    }

    /** A reported count as a non-negative number, treating absent as none. Shared with replacement readers. */
    protected static long count(@Nullable Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
