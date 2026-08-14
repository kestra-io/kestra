package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic a spend ceiling is judged by, and the defaults it is judged by when nobody configured one.
 *
 * <p>The weighing cases state their own weights rather than borrowing {@link AiUsageLimitConfiguration#DISABLED}'s:
 * those figures track a rate card and will be re-priced, and a test that reads them from the constant would then
 * fail for a reason that has nothing to do with the arithmetic it is checking. Only
 * {@link #shouldRecordButNeitherShowNorEnforceByDefault()} names the constant, because the constant is its subject.
 */
class AiUsageLimitConfigurationTest {
    /** Limits switched on with an explicit rate card, so each expected total is derivable from the case itself. */
    private static AiUsageLimitConfiguration weights(
        final double coldInput, final double cachedInput, final double output) {
        return new AiUsageLimitConfiguration(
            true, coldInput, cachedInput, output, 1_000_000, 0, 10, Duration.ofDays(30)
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

        // Then it costs 1,000 + 100 + 6,000 units. A raw sum would have said 4,000 and been wrong by more than
        // a factor of two — which is the entire reason the ceiling is expressed in weighted units.
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
        // Given a provider priced differently from the defaults — a self-hosted model with no cache discount and
        // cheap output, which is exactly why the weights are per provider
        AiUsageLimitConfiguration configuration = weights(1.0, 1.0, 2.0);
        AiUsageTotals totals = new AiUsageTotals(2_000, 1_000, 1_000, 0);

        // Then the configured card is what is charged: 1,000 + 1,000 + 2,000, not the 7,100 the defaults give.
        // This is the case that would keep passing if weigh() ignored the configuration and hardcoded the rates.
        assertThat(configuration.weigh(totals)).isEqualTo(4_000);
    }

    @Test
    void shouldEnforceNothingWhenEnabledWithoutACeiling() {
        // Given limits switched on but no ceiling set
        AiUsageLimitConfiguration configuration =
            new AiUsageLimitConfiguration(true, 1.0, 0.1, 6.0, 0, 0, 10, null);

        // Then there is nothing to enforce. Failing open here is deliberate: a zero ceiling is far more likely
        // to be an unset property than an operator asking for no AI at all, and reading it as the latter would
        // break Copilot for anyone who enabled limits before sizing them.
        assertThat(configuration.isEnforceable()).isFalse();
    }
}
