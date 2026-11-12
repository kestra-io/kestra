package io.kestra.core.ai.usage.models;

/**
 * Summed token counts for one axis over one window.
 *
 * <p>Deliberately not a cost: the weights that turn these into a comparable figure belong to the provider's
 * configuration, so that changing them re-prices history rather than invalidating it.
 */
public record AiUsageTotals(
    long promptTokens,
    long cachedPromptTokens,
    long completionTokens,
    long thoughtTokens
) {
    public static final AiUsageTotals ZERO = new AiUsageTotals(0, 0, 0, 0);

    /** The uncached share of the prompt — the part billed at the full input rate. */
    public long coldPromptTokens() {
        return Math.max(0, promptTokens - cachedPromptTokens);
    }

    /** Thoughts bill at the output rate, so they belong with output when weighting. */
    public long outputTokens() {
        return completionTokens + thoughtTokens;
    }
}
