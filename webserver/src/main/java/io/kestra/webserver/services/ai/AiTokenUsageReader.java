package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Reads the token counts of one model call off whatever {@link TokenUsage} the provider returned.
 *
 * <p>langchain4j reports the two counts that matter most for cost — the cached share of the prompt and thinking
 * tokens — only on provider-specific subclasses, so they cannot be read from the common type. Everything the
 * base type offers is read for every provider; the rest is read where the subclass is known.
 *
 * <p>A bean rather than a static helper so Enterprise can replace it and add the subclasses only it has on its
 * classpath (OpenAI, Anthropic, Bedrock, Mistral, Ollama). Until it does, those providers report a prompt total
 * with no cached share, which weighs the whole prompt at the cold rate — an overestimate of cost, which is the
 * safe direction for a ceiling to err in.
 */
@Singleton
public class AiTokenUsageReader {
    /**
     * The counts for a single call, all zero when the provider reported none.
     *
     * <p>A provider reporting nothing is not an error: the relayed free tier does exactly that when a turn fails
     * mid-stream, and a turn that cost nothing measurable should record nothing rather than fail.
     */
    public AiUsageTotals read(@Nullable TokenUsage usage) {
        if (usage == null) {
            return AiUsageTotals.ZERO;
        }

        long prompt = count(usage.inputTokenCount());
        // Gemini's outputTokenCount is candidatesTokenCount, which excludes thoughts, so the two add rather than
        // overlap. Reading them as overlapping would undercount the most expensive tokens in a thinking model.
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

    /**
     * A reported count as a non-negative number, treating absent as none.
     *
     * <p>Visible to subclasses because a replacement reader reads the same optional {@code Integer} counts off
     * its own provider's subclasses, and a second copy of this would be a second place for a null or a negative
     * to slip through.
     */
    protected static long count(@Nullable Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
