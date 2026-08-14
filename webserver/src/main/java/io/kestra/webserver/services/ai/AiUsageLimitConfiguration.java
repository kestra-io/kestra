package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-provider spend ceiling, expressed in weighted tokens.
 *
 * <p>Weighted rather than raw, because a raw token sum is nearly as wrong as a call count: cached input bills at
 * a fraction of cold input and output bills at a multiple of it, so the same token total can differ in cost by
 * more than an order of magnitude. One weighted unit is one cold input token, which makes a ceiling convertible
 * to money by one multiplication and leaves stored history re-priceable when a rate card moves.
 *
 * <p>Hangs off {@link AiConfiguration}, so each provider carries its own weights and ceilings — a customer's own
 * OpenAI key and Kestra's hosted free tier have neither the same rates nor the same reason to be capped.
 *
 * <p><b>Disabled by default, but usage is recorded regardless.</b> Switching this on therefore reports against
 * history that already exists, instead of starting from zero and looking wrong for a day.
 *
 * @param enabled                  whether the ceiling is shown to users and enforced; recording does not depend
 *                                 on it
 * @param maxWeight                installation-wide ceiling per window, across every caller
 * @param userMaxWeight            ceiling for a single caller. Separate from {@link #maxWeight} on purpose: one
 *                                 figure applied to both axes would let a single user exhaust the installation
 *                                 and make the per-user axis decorative
 * @param warningThresholdPercent  remaining percentage below which a caller should be warned, so exhaustion is
 *                                 something a user sees coming rather than discovers
 * @param window                   how far back a ceiling looks. A ceiling with no window would lock an
 *                                 installation out permanently the day it is first reached, so this has a
 *                                 default rather than being optional
 *
 * <p>Also bound from the hosted relay's {@code /limits} response, which is why unknown properties are ignored: the
 * relay is deployed separately and will add fields, and an instance must keep reading the ones it understands
 * rather than failing to read any of them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiUsageLimitConfiguration(
    @Bindable(defaultValue = "false") boolean enabled,

    @Bindable(defaultValue = "1.0") double coldInputWeight,
    @Bindable(defaultValue = "0.1") double cachedInputWeight,
    @Bindable(defaultValue = "6.0") double outputWeight,

    @Bindable(defaultValue = "0") long maxWeight,
    @Bindable(defaultValue = "0") long userMaxWeight,

    @Bindable(defaultValue = "10") int warningThresholdPercent,

    @Bindable(defaultValue = "P30D") Duration window
) {
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    public AiUsageLimitConfiguration {
        if (coldInputWeight == 0) {
            coldInputWeight = 1.0;
        }
        if (cachedInputWeight == 0) {
            cachedInputWeight = 0.1;
        }
        if (outputWeight == 0) {
            outputWeight = 6.0;
        }
        if (warningThresholdPercent == 0) {
            warningThresholdPercent = 10;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = DEFAULT_WINDOW;
        }
    }

    /**
     * Converts reported counts into weighted units.
     *
     * <p>When a provider reports no cached share, cold prompt tokens equal the whole prompt and this degrades to
     * prompt x1 plus output x6 with no special case — still most of the way to the truth, where a raw sum is not.
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

    /** The lower bound of the current window, which is what the repository is queried with. */
    public Instant windowStart(Instant now) {
        return now.minus(window);
    }
}
