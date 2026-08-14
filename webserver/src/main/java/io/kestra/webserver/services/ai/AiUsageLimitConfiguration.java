package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micronaut.core.bind.annotation.Bindable;

import java.time.Instant;

/**
 * Per-provider spend ceiling, expressed in weighted tokens.
 *
 * <p>Weighted rather than raw because cached input bills at a fraction of cold input and output at a multiple
 * of it, so the same token total can differ in cost by an order of magnitude. One weighted unit is one cold
 * input token, which keeps stored history re-priceable when a rate card moves.
 *
 * <p>Declaring the block is what asks for a ceiling: a provider with no {@code usage-limit} has nothing shown
 * or enforced, which is the default. Usage is recorded either way.
 *
 * <p>Also bound from the hosted relay's {@code /limits} response, which is why unknown properties are ignored —
 * the relay is deployed separately and will add fields.
 *
 * @param enabled                  whether the ceiling is shown and enforced; recording does not depend on it.
 *                                 Boxed so an omitted flag can default to true while an explicit {@code false}
 *                                 keeps the figures and suspends enforcement
 * @param coldInputWeight          cost of one uncached prompt token, and the unit the other weights are
 *                                 expressed in. Boxed, like the two below, so an omitted weight takes the
 *                                 default while an explicit {@code 0} is honoured — a self-hosted model whose
 *                                 input is genuinely free
 * @param cachedInputWeight        cost of one prompt token served from the provider's cache
 * @param outputWeight             cost of one generated token, thinking included
 * @param maxWeight                installation-wide ceiling per window, across every caller and every tenant:
 *                                 the provider key is the installation's and is billed to it as one bill
 * @param userMaxWeight            ceiling for a single caller, likewise across every tenant they can reach.
 *                                 Separate from {@link #maxWeight} so one user cannot exhaust the installation
 * @param warningThresholdPercent  remaining percentage below which a caller should be warned; explicit
 *                                 {@code 0} suppresses the warning until the allowance is gone
 * @param window                   the period a ceiling is counted over, and at whose boundary spend starts again
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiUsageLimitConfiguration(
    @Bindable(defaultValue = "true") Boolean enabled,

    @Bindable(defaultValue = "1.0") Double coldInputWeight,
    @Bindable(defaultValue = "0.1") Double cachedInputWeight,
    @Bindable(defaultValue = "6.0") Double outputWeight,

    @Bindable(defaultValue = "0") long maxWeight,
    @Bindable(defaultValue = "0") long userMaxWeight,

    @Bindable(defaultValue = "10") Integer warningThresholdPercent,

    @Bindable(defaultValue = "MONTHLY") AiUsageWindow window
) {
    private static final AiUsageWindow DEFAULT_WINDOW = AiUsageWindow.MONTHLY;
    private static final double DEFAULT_COLD_INPUT_WEIGHT = 1.0;
    private static final double DEFAULT_CACHED_INPUT_WEIGHT = 0.1;
    private static final double DEFAULT_OUTPUT_WEIGHT = 6.0;
    private static final int DEFAULT_WARNING_THRESHOLD_PERCENT = 10;

    public AiUsageLimitConfiguration {
        // Not left to @Bindable, which governs configuration binding alone: the relay's /limits response
        // arrives through Jackson, where an omitted field stays null.
        if (enabled == null) {
            enabled = true;
        }
        if (coldInputWeight == null) {
            coldInputWeight = DEFAULT_COLD_INPUT_WEIGHT;
        }
        if (cachedInputWeight == null) {
            cachedInputWeight = DEFAULT_CACHED_INPUT_WEIGHT;
        }
        if (outputWeight == null) {
            outputWeight = DEFAULT_OUTPUT_WEIGHT;
        }
        if (warningThresholdPercent == null) {
            warningThresholdPercent = DEFAULT_WARNING_THRESHOLD_PERCENT;
        }
        if (window == null) {
            window = DEFAULT_WINDOW;
        }
    }

    /**
     * Converts reported counts into weighted units. A provider reporting no cached share degrades to the whole
     * prompt at the cold rate, with no special case.
     */
    public long weigh(AiUsageTotals totals) {
        return Math.round(
            totals.coldPromptTokens() * coldInputWeight
                + totals.cachedPromptTokens() * cachedInputWeight
                + totals.outputTokens() * outputWeight
        );
    }

    /** A ceiling of zero means unset, so enabling limits without setting one enforces nothing. */
    public boolean isEnforceable() {
        return enabled && (maxWeight > 0 || userMaxWeight > 0);
    }

    /** The lower bound the repository is queried with. */
    public Instant windowStart(Instant now) {
        return window.start(now);
    }

    /** When the window ends and a refused caller can run again. */
    public Instant windowEnd(Instant now) {
        return window.next(now);
    }
}
