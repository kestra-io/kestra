package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic a spend ceiling is judged by, and the defaults applied when nobody configured one.
 *
 * <p>The weighing cases state their own weights rather than reading the shipped defaults, which track a rate
 * card and will be re-priced — otherwise they would fail for reasons unrelated to the arithmetic under test.
 */
class AiUsageLimitConfigurationTest {
    /** Limits on with an explicit rate card, so each expected total is derivable from the case itself. */
    private static AiUsageLimitConfiguration weights(
        final double coldInput, final double cachedInput, final double output) {
        return new AiUsageLimitConfiguration(
            true, coldInput, cachedInput, output, 1_000_000, 0, 10, AiUsageWindow.MONTHLY
        );
    }

    @Test
    void shouldWeighCachedInputAtAFractionAndOutputAtAMultiple() {
        // Given cached input priced at a tenth of cold and output at six times it...
        AiUsageLimitConfiguration configuration = weights(1.0, 0.1, 6.0);
        // ...and a turn of 1,000 cold, 1,000 cached and 1,000 output tokens
        AiUsageTotals totals = new AiUsageTotals(2_000, 1_000, 1_000, 0);

        // When weighed
        long weighted = configuration.weigh(totals);

        // Then it costs 1,000 + 100 + 6,000 units, where a raw sum would have said 4,000
        assertThat(weighted).isEqualTo(7_100);
    }

    @Test
    void shouldChargeThinkingAtTheOutputRate() {
        // Given a turn whose thinking dwarfs its visible output, as an agent turn's does
        AiUsageTotals totals = new AiUsageTotals(1_284, 1_152, 19, 128);

        // Then thinking is priced with output, not ignored
        assertThat(weights(1.0, 0.1, 6.0).weigh(totals))
            .isEqualTo(132 + 115 + (19 + 128) * 6);
    }

    @Test
    void shouldDegradeToPromptTimesOneWhenAProviderReportsNoCachedShare() {
        // Given a provider that reports no cached tokens, which most do not
        AiUsageTotals totals = new AiUsageTotals(1_000, 0, 100, 0);

        // Then the whole prompt is treated as cold — the intended fallback, reached with no special case
        assertThat(weights(1.0, 0.1, 6.0).weigh(totals)).isEqualTo(1_000 + 600);
    }

    @Test
    void shouldApplyTheConfiguredWeightsRatherThanAnyFixedRateCard() {
        // Given a provider priced differently from the defaults, which is why the weights are per provider
        AiUsageLimitConfiguration configuration = weights(1.0, 1.0, 2.0);
        AiUsageTotals totals = new AiUsageTotals(2_000, 1_000, 1_000, 0);

        // Then the configured card is charged: 1,000 + 1,000 + 2,000, not the 7,100 the defaults give
        assertThat(configuration.weigh(totals)).isEqualTo(4_000);
    }

    @Test
    void shouldHonourAWeightExplicitlySetToZero() {
        // Given a self-hosted model whose input costs nothing, priced as such
        AiUsageLimitConfiguration configuration =
            new AiUsageLimitConfiguration(true, 0.0, 0.0, 2.0, 1_000_000, 0, 10, AiUsageWindow.MONTHLY);
        AiUsageTotals totals = new AiUsageTotals(5_000, 2_000, 1_000, 0);

        // Then only output is charged. Rewriting an explicit zero to the default would bill 3,000 free input
        // tokens at the cold rate and put the operator against a ceiling they priced themselves out of.
        assertThat(configuration.weigh(totals)).isEqualTo(2_000);
    }

    @Test
    void shouldFallBackToTheDefaultWeightsWhenNoneAreGiven() {
        // Given the relay's JSON, or a configuration block, that names ceilings and no rate card at all
        AiUsageLimitConfiguration configuration =
            new AiUsageLimitConfiguration(null, null, null, null, 1_000_000, 0, null, null);

        // Then the defaults apply — absent is still absent, which is the distinction the boxing exists to keep
        assertThat(configuration.coldInputWeight()).isEqualTo(1.0);
        assertThat(configuration.cachedInputWeight()).isEqualTo(0.1);
        assertThat(configuration.outputWeight()).isEqualTo(6.0);
        assertThat(configuration.warningThresholdPercent()).isEqualTo(10);
    }

    @Test
    void shouldEnforceNothingWhenEnabledWithoutACeiling() {
        // Given limits switched on but no ceiling set
        AiUsageLimitConfiguration configuration =
            new AiUsageLimitConfiguration(true, 1.0, 0.1, 6.0, 0, 0, 10, null);

        // Then there is nothing to enforce: a zero ceiling reads as unset rather than as "no AI at all"
        assertThat(configuration.isEnforceable()).isFalse();
    }
}
