package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.ai.usage.repositories.AiUsageRepositoryInterface;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Recording and ceilings on both axes: the installation and the caller. Neither is scoped by tenant, and both
 * are asserted to cross that boundary.
 *
 * <p>Real providers and the real store throughout — each ceiling is declared the way an operator declares one,
 * and the totals are summed by H2 from rows this service wrote. The parts likeliest to be wrong are the
 * migration's generated columns and the binding of a limit out of configuration, which a stub would not cover.
 *
 * <p>Every scenario owns a provider id, since the tests share one database and totals are read per provider.
 */
@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiUsageServiceTest implements TestPropertyProvider {
    static final String NO_CEILING = "gemini-without-a-ceiling";
    static final String INSTALLATION_CEILING = "gemini-with-an-installation-ceiling";
    static final String PER_USER_CEILING = "gemini-with-a-per-user-ceiling";
    static final String UNATTRIBUTED_SPEND = "gemini-metering-unattributed-spend";
    static final String SHORT_WINDOW = "gemini-with-a-daily-window";
    static final String SHORT_WINDOW_EXHAUSTED = "gemini-with-a-monthly-window-exhausted";
    static final String INSTALLATION_CEILING_UNREACHED = "gemini-with-a-ceiling-nobody-reached";
    static final String CEILING_ACROSS_TENANTS = "gemini-metering-two-tenants";
    static final String PER_USER_CEILING_ACROSS_TENANTS = "gemini-metering-one-user-across-tenants";
    static final String GAUGED_CEILING = "gemini-with-a-gauged-ceiling";
    static final String REFUSED_PER_USER_CEILING = "gemini-refusing-one-caller";

    private final Map<String, String> properties = new LinkedHashMap<>();
    private int providerIndex;

    /**
     * The providers these tests meter against, declared as an operator would rather than constructed directly.
     * Built here rather than as fifty {@code @Property} annotations, so the ceiling that distinguishes each
     * scenario is the only line written per provider.
     *
     * <p>None says {@code enabled}: declaring the block is the whole of asking for a ceiling.
     */
    @Override
    public @NonNull Map<String, String> getProperties() {
        properties.clear();
        providerIndex = 0;

        // No ceiling — what an installation looks like before anyone sets one.
        declareProvider(NO_CEILING, Map.of());
        properties.put("kestra.ai.providers[0].is-default", "true");

        declareProvider(INSTALLATION_CEILING, Map.of("max-weight", "10000"));
        declareProvider(PER_USER_CEILING, Map.of("max-weight", "1000000", "user-max-weight", "5000"));
        declareProvider(UNATTRIBUTED_SPEND, Map.of("max-weight", "1000", "user-max-weight", "100"));
        declareProvider(SHORT_WINDOW, Map.of("max-weight", "1000", "window", "DAILY"));
        declareProvider(SHORT_WINDOW_EXHAUSTED, Map.of("max-weight", "1000", "window", "MONTHLY"));
        declareProvider(INSTALLATION_CEILING_UNREACHED, Map.of("max-weight", "1000000"));
        declareProvider(CEILING_ACROSS_TENANTS, Map.of("max-weight", "10000"));
        declareProvider(PER_USER_CEILING_ACROSS_TENANTS, Map.of("max-weight", "1000000", "user-max-weight", "5000"));

        // The metric scenarios own their providers too, since a gauge reads whatever every other test spent.
        declareProvider(GAUGED_CEILING, Map.of("max-weight", "10000"));
        declareProvider(REFUSED_PER_USER_CEILING, Map.of("max-weight", "1000000", "user-max-weight", "5000"));

        return properties;
    }

    /** One provider: the id, the shared boilerplate, and the ceiling that distinguishes it. */
    private void declareProvider(final String id, final Map<String, String> usageLimit) {
        String provider = "kestra.ai.providers[%d]".formatted(providerIndex++);
        properties.put(provider + ".id", id);
        properties.put(provider + ".type", "gemini");
        properties.put(provider + ".configuration.api-key", "fake-key");
        usageLimit.forEach((key, value) -> properties.put(provider + ".configuration.usage-limit." + key, value));
    }

    private static final String TENANT = TenantService.MAIN_TENANT;
    /** A second tenant, for the cases asserting that both axes count spend from beyond the calling one. */
    private static final String OTHER_TENANT = "a-neighbouring-tenant";
    private static final String MODEL = "gemini-3.1-flash-lite";

    @Inject
    AiUsageService service;

    @Inject
    AiUsageRepositoryInterface usageStore;

    @Inject
    AiServiceManager aiServiceManager;

    @Inject
    AiUsageMetrics usageMetrics;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void shouldRecordUsageEvenWhileLimitsAreDisabled() {
        // Given a provider with no limits configured, which is the default
        String provider = uniqueProvider();
        Instant windowStart = Instant.now().minus(Duration.ofHours(1));

        // When a call is recorded
        service.record(TENANT, provider, "user-1", MODEL, GoogleAiGeminiTokenUsage.builder()
            .inputTokenCount(1_000)
            .outputTokenCount(19)
            .cachedContentTokenCount(400)
            .thoughtsTokenCount(128)
            .build());

        // Then it is still written. This is the whole reason recording is not gated on the limit: switching one on
        // later reports against history that exists, rather than reading as zero spend for a window.
        AiUsageTotals totals = usageStore.totals(provider, windowStart);
        assertThat(totals.promptTokens()).isEqualTo(1_000);
        assertThat(totals.cachedPromptTokens()).isEqualTo(400);
        assertThat(totals.completionTokens()).isEqualTo(19);
        assertThat(totals.thoughtTokens()).isEqualTo(128);
        // and attributed to the caller, which is what the per-user axis is later summed by
        assertThat(usageStore.totalsForUser(provider, "user-1", windowStart).promptTokens()).isEqualTo(1_000);
    }

    @Test
    void shouldRecordUnderTheDefaultProviderWhenTheCallerNamedNone() {
        // Given a caller that left the provider unnamed, as the chat endpoint allows
        Instant windowStart = Instant.now().minus(Duration.ofHours(1));
        AiUsageTotals before = usageStore.totals(aiServiceManager.getDefaultProviderId(), windowStart);

        // When
        service.record(TENANT, null, null, MODEL, new TokenUsage(10, 5, 15));

        // Then the row names the provider the turn ran against; recording "none" would split one provider's
        // history across two keys
        AiUsageTotals after = usageStore.totals(aiServiceManager.getDefaultProviderId(), windowStart);
        assertThat(after.promptTokens() - before.promptTokens()).isEqualTo(10);
        assertThat(after.completionTokens() - before.completionTokens()).isEqualTo(5);
    }

    @Test
    void shouldWriteNothingWhenTheProviderReportedNoUsage() {
        // Given a call the provider reported no counts for — a turn that failed mid-stream
        String provider = uniqueProvider();
        service.record(TENANT, provider, null, null, null);

        // Then no row is written, rather than a row of zeroes that adds nothing but rows
        assertThat(usageStore.totals(provider, Instant.now().minus(Duration.ofHours(1))))
            .isEqualTo(AiUsageTotals.ZERO);
    }

    @Test
    void shouldNotFailTheTurnWhenRecordingFails() {
        // Given a store that cannot be written to, which the real one cannot be asked to be on demand
        AiUsageService unrecordable =
            new AiUsageService(new UnwritableAiUsageRepository(), aiServiceManager, new AiTokenUsageReader(), usageMetrics);

        // When a completed call is recorded
        // Then the caller is not affected. The tokens are already spent and the user could do nothing about a
        // bookkeeping failure, so failing their turn over it would trade a silent undercount for a visible outage.
        assertThatCode(() -> unrecordable.record(TENANT, uniqueProvider(), null, MODEL, new TokenUsage(10, 5, 15)))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldReportNothingWhenLimitsAreDisabled() {
        // Given a provider that declares no limit, and spend recorded against it
        service.record(TENANT, NO_CEILING, "user-1", MODEL, new TokenUsage(50_000, 50_000, 100_000));

        // When the status is asked for
        AiUsageStatus status = service.status(NO_CEILING, "user-1");

        // Then nothing is shown and nothing is enforced, however much has been spent: usage is recorded for every
        // provider, and a provider that asked for no ceiling is held to none.
        assertThat(status.enabled()).isFalse();
        assertThat(status.isExceeded()).isFalse();
        assertThat(status.global()).isNull();
        assertThat(status.user()).isNull();
    }

    @Test
    void shouldRefuseWhenTheInstallationWideCeilingIsReached() {
        // Given an installation-wide ceiling of 10,000 weighted tokens...
        // ...and spend of 1,000 cold prompt tokens plus 1,600 output = 1,000 + 9,600 weighted
        service.record(TENANT, INSTALLATION_CEILING, null, MODEL, new TokenUsage(1_000, 1_600, 2_600));

        // When
        AiUsageStatus status = service.status(INSTALLATION_CEILING, null);

        // Then the ceiling binds on weight, not on raw tokens: 2,600 tokens are under any raw reading of a
        // 10,000 limit, while their cost is over it. That difference is the reason weights exist.
        assertThat(status.global().weight()).isEqualTo(10_600);
        assertThat(status.isExceeded()).isTrue();
        assertThat(status.exceededMessage()).contains("This installation has reached");
    }

    @Test
    void shouldCountEveryTenantsSpendAgainstTheInstallationCeiling() {
        // Given a 10,000 ceiling and 7,000 weighted tokens spent under each of two tenants — either alone would
        // sit under it
        service.record(TENANT, CEILING_ACROSS_TENANTS, null, MODEL, new TokenUsage(1_000, 1_000, 2_000));
        service.record(OTHER_TENANT, CEILING_ACROSS_TENANTS, null, MODEL, new TokenUsage(1_000, 1_000, 2_000));

        // When either tenant is asked about
        AiUsageStatus status = service.status(CEILING_ACROSS_TENANTS, null);

        // Then their spend is summed and the ceiling is exhausted. The provider key belongs to the installation
        // and arrives as one bill, so the ceiling that protects it counts the whole of what was spent against it.
        assertThat(status.global().weight()).isEqualTo(14_000);
        assertThat(status.isExceeded()).isTrue();
    }

    @Test
    void shouldCountAUsersSpendFromEveryTenantAgainstTheirOwnCeiling() {
        // Given one user spending under two tenants, half their 5,000 allowance in each
        service.record(TENANT, PER_USER_CEILING_ACROSS_TENANTS, "user-1", MODEL, new TokenUsage(1_000, 250, 1_250));
        service.record(OTHER_TENANT, PER_USER_CEILING_ACROSS_TENANTS, "user-1", MODEL, new TokenUsage(1_000, 250, 1_250));

        // When they are asked about from one of those tenants
        AiUsageStatus status = service.status(PER_USER_CEILING_ACROSS_TENANTS, "user-1");

        // Then both tenants count against the one personal allowance, and it is exhausted. A user is one identity
        // however many tenants they hold access to; a per-tenant reading would let them spend the allowance twice.
        assertThat(status.user().weight()).isEqualTo(5_000);
        assertThat(status.user().exceeded()).isTrue();
        // while the installation, whose ceiling is far higher, is nowhere near its own
        assertThat(status.global().weight()).isEqualTo(5_000);
        assertThat(status.global().exceeded()).isFalse();
    }

    @Test
    void shouldRefuseTheCallerAloneWhenOnlyTheirOwnCeilingIsReached() {
        // Given plenty of installation-wide allowance but a small per-user one, one user who has spent theirs,
        // and as much again that is nobody's
        service.record(TENANT, PER_USER_CEILING, "user-1", MODEL, new TokenUsage(5_000, 0, 5_000));
        service.record(TENANT, PER_USER_CEILING, null, MODEL, new TokenUsage(5_000, 0, 5_000));

        // When
        AiUsageStatus status = service.status(PER_USER_CEILING, "user-1");

        // Then that user is refused while the installation is not. One figure applied to both axes would make
        // the per-user axis decorative: whoever got there first would spend everyone else's allowance.
        assertThat(status.user().exceeded()).isTrue();
        assertThat(status.global().weight()).isEqualTo(10_000);
        assertThat(status.global().maxWeight()).isEqualTo(1_000_000);
        assertThat(status.global().exceeded()).isFalse();
        assertThat(status.isExceeded()).isTrue();
        assertThat(status.exceededMessage()).contains("You have reached your AI usage limit");
    }

    @Test
    void shouldEvaluateOnlyTheInstallationAxisWhenThereIsNoCaller() {
        // Given an OSS install, where the agent path has no user identity at all, and spend that sits under the
        // installation's 1,000 ceiling while being five times the 100 a single caller is allowed
        service.record(TENANT, UNATTRIBUTED_SPEND, null, MODEL, new TokenUsage(500, 0, 500));

        // When
        AiUsageStatus status = service.status(UNATTRIBUTED_SPEND, null);

        // Then the per-user axis is not evaluated at all. Querying it with a null user would return every
        // unattributed row of every tenant — so a 100-token personal ceiling would lock the install out at the
        // same moment its 1,000-token one did, and sooner still on an installation with more than one tenant.
        assertThat(status.user()).isNull();
        assertThat(status.global().weight()).isEqualTo(500);
        assertThat(status.global().maxWeight()).isEqualTo(1_000);
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    void shouldReportNothingWhenTheProviderIsUnknown() {
        // Given a provider id that resolves to no service
        // Then the answer is "no limit" rather than a failure: an unknown provider is already rejected with 503
        // by the endpoints, and reporting on it is not this service's job.
        assertThat(service.status("nope", null).enabled()).isFalse();
    }

    @Test
    void shouldCountOnlyTheCurrentPeriodRatherThanAllHistory() {
        // Given a daily ceiling, spend from ten days ago that would exhaust it on its own...
        usageStore.save(new AiUsage(
            IdUtils.create(), TENANT, SHORT_WINDOW, null, MODEL, Instant.now().minus(Duration.ofDays(10)),
            5_000, 0, 0, 0
        ));
        // ...and a little spend inside today's
        service.record(TENANT, SHORT_WINDOW, null, MODEL, new TokenUsage(100, 0, 100));

        // When
        Instant before = Instant.now();
        AiUsageStatus status = service.status(SHORT_WINDOW, null);

        // Then only what falls inside the current period counts, and it is summed from that period's boundary
        // rather than from a moving point. Summing all history instead would make the first exhaustion permanent.
        assertThat(status.windowStart()).isEqualTo(AiUsageWindow.DAILY.start(before));
        assertThat(status.global().weight()).isEqualTo(100);
        assertThat(status.global().maxWeight()).isEqualTo(1_000);
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    void shouldReportWhenAnExhaustedCeilingStartsAgain() {
        // Given a monthly ceiling exhausted by a single call in the current period
        Instant before = Instant.now();
        service.record(TENANT, SHORT_WINDOW_EXHAUSTED, null, MODEL, new TokenUsage(2_000, 0, 2_000));

        // When
        AiUsageStatus status = service.status(SHORT_WINDOW_EXHAUSTED, null);

        // Then the caller is told the date the period turns over, which is the whole reason the ceiling counts
        // calendar periods: a rolling one has no such date to give them.
        assertThat(status.isExceeded()).isTrue();
        assertThat(status.availableAt()).isEqualTo(AiUsageWindow.MONTHLY.next(before));
        assertThat(status.exceededMessage()).contains("It resets on");
    }

    @Test
    void shouldReportNoMomentWhileTheCeilingIsNotExhausted() {
        // Given spend that sits under the ceiling
        service.record(TENANT, INSTALLATION_CEILING_UNREACHED, null, MODEL, new TokenUsage(100, 0, 100));

        // When / Then there is nothing to wait for, so nothing is reported — and the store is not read a second
        // time to work that out, since this runs before every model call.
        AiUsageStatus status = service.status(INSTALLATION_CEILING_UNREACHED, null);
        assertThat(status.isExceeded()).isFalse();
        assertThat(status.availableAt()).isNull();
    }

    @Test
    void shouldPublishTokenCountersWhenACallIsRecorded() {
        // Given a provider nothing else meters against, so the counters start empty
        String provider = uniqueProvider();

        // When a call reporting every count is recorded
        service.record(TENANT, provider, "user-1", MODEL, GoogleAiGeminiTokenUsage.builder()
            .inputTokenCount(1_000)
            .outputTokenCount(19)
            .cachedContentTokenCount(400)
            .thoughtsTokenCount(128)
            .build());

        // Then the same counts the store holds are published, broken down by provider, model and tenant — the
        // dashboard and the stored history are fed from one reading so they cannot disagree
        assertThat(counter(MetricRegistry.METRIC_AI_MODEL_CALL_TOTAL, provider)).isEqualTo(1);
        assertThat(counter(MetricRegistry.METRIC_AI_TOKEN_PROMPT_TOTAL, provider)).isEqualTo(1_000);
        assertThat(counter(MetricRegistry.METRIC_AI_TOKEN_PROMPT_CACHED_TOTAL, provider)).isEqualTo(400);
        assertThat(counter(MetricRegistry.METRIC_AI_TOKEN_COMPLETION_TOTAL, provider)).isEqualTo(19);
        assertThat(counter(MetricRegistry.METRIC_AI_TOKEN_THOUGHT_TOTAL, provider)).isEqualTo(128);
    }

    @Test
    void shouldCountACallEvenWhenTheProviderReportedNoUsage() {
        // Given a call the provider reported no counts for
        String provider = uniqueProvider();
        service.record(TENANT, provider, null, MODEL, null);

        // Then the call is still counted, while nothing is charged to the token counters. A call rate that
        // omitted these would read as an idle Copilot at exactly the moment a provider stopped reporting.
        assertThat(counter(MetricRegistry.METRIC_AI_MODEL_CALL_TOTAL, provider)).isEqualTo(1);
        assertThat(counter(MetricRegistry.METRIC_AI_TOKEN_PROMPT_TOTAL, provider)).isZero();
    }

    @Test
    void shouldCountAUsageRecordThatCouldNotBeStored() {
        // Given a store that cannot be written to
        String provider = uniqueProvider();
        AiUsageService unrecordable =
            new AiUsageService(new UnwritableAiUsageRepository(), aiServiceManager, new AiTokenUsageReader(), usageMetrics);

        // When a completed call is recorded
        unrecordable.record(TENANT, provider, null, MODEL, new TokenUsage(10, 5, 15));

        // Then the drop is visible. Swallowing the failure keeps the turn alive, so this counter is the only
        // thing that says the stored totals are now an undercount.
        assertThat(counter(MetricRegistry.METRIC_AI_USAGE_RECORD_FAILED_TOTAL, provider)).isEqualTo(1);
    }

    @Test
    void shouldPublishTheInstallationGaugesWhenAStatusIsComputed() {
        // Given a 10,000 ceiling with 2,000 weighted tokens spent against it
        service.record(TENANT, GAUGED_CEILING, null, MODEL, new TokenUsage(2_000, 0, 2_000));

        // When the status is computed, which happens before every model call
        AiUsageStatus status = service.status(GAUGED_CEILING, null);

        // Then the installation axis is gauged. Only that axis: a per-user gauge would carry one series per
        // user, and the tokens a single user spends stay visible on the counters.
        assertThat(gauge(MetricRegistry.METRIC_AI_USAGE_WEIGHT, GAUGED_CEILING)).isEqualTo(2_000);
        assertThat(gauge(MetricRegistry.METRIC_AI_USAGE_WEIGHT_LIMIT, GAUGED_CEILING)).isEqualTo(10_000);
        // The remaining allowance is published as a fraction rather than a percentage, per the metric
        // conventions — a threshold reads the same whatever the ceiling is set to
        assertThat(status.remainingPercent()).isEqualTo(80);
        assertThat(gauge(MetricRegistry.METRIC_AI_USAGE_REMAINING_RATIO, GAUGED_CEILING)).isEqualTo(0.8);
    }

    @Test
    void shouldPublishNoGaugeForAProviderWithoutACeiling() {
        // Given a provider that declares no limit
        service.status(NO_CEILING, null);

        // Then it is not gauged as a ceiling of zero, which would read as a provider permanently exhausted
        assertThat(meterRegistry.getMeters())
            .noneMatch(meter -> meter.getId().getName().endsWith(MetricRegistry.METRIC_AI_USAGE_WEIGHT_LIMIT)
                && NO_CEILING.equals(meter.getId().getTag(MetricRegistry.TAG_AI_PROVIDER_ID)));
    }

    @Test
    void shouldCountARefusalAgainstTheAxisThatRanOut() {
        // Given a caller who has spent their own allowance while the installation has plenty left
        service.record(TENANT, REFUSED_PER_USER_CEILING, "user-refused", MODEL, new TokenUsage(5_000, 0, 5_000));
        AiUsageStatus status = service.status(REFUSED_PER_USER_CEILING, "user-refused");
        assertThat(status.isExceeded()).isTrue();

        // When the turn is stopped for it
        service.recordRefusal(status);

        // Then the refusal names the personal axis, since a user waiting out their own allowance and an
        // installation-wide outage are the same counter and different incidents
        assertThat(counter(MetricRegistry.METRIC_AI_USAGE_REFUSED_TOTAL, REFUSED_PER_USER_CEILING, "user")).isEqualTo(1);
    }

    /** The counter for one provider, matched on the suffix so the configured metric prefix is irrelevant. */
    private double counter(final String name, final String providerId) {
        return counter(name, providerId, null);
    }

    private double counter(final String name, final String providerId, @Nullable final String axis) {
        return meters(name, providerId, axis)
            .filter(Counter.class::isInstance)
            .mapToDouble(meter -> ((Counter) meter).count())
            .sum();
    }

    private double gauge(final String name, final String providerId) {
        return meters(name, providerId, null)
            .filter(Gauge.class::isInstance)
            .mapToDouble(meter -> ((Gauge) meter).value())
            .sum();
    }

    private Stream<Meter> meters(final String name, final String providerId, @Nullable final String axis) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().endsWith(name))
            .filter(meter -> providerId.equals(meter.getId().getTag(MetricRegistry.TAG_AI_PROVIDER_ID)))
            .filter(meter -> axis == null || axis.equals(meter.getId().getTag(MetricRegistry.TAG_AI_USAGE_AXIS)));
    }

    /** A provider nothing else meters against, for the recording paths that read no limit. */
    private static String uniqueProvider() {
        return "gemini-" + IdUtils.create();
    }

    /**
     * A store whose writes fail, since a real one cannot be asked to fail on demand and dropping its table would
     * take the rest of the suite's schema with it. Only the write path is exercised through this.
     */
    private static final class UnwritableAiUsageRepository implements AiUsageRepositoryInterface {
        @Override
        public AiUsage save(final AiUsage usage) {
            throw new IllegalStateException("The database is down.");
        }

        @Override
        public AiUsageTotals totals(final String providerId, final Instant from) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiUsageTotals totalsForUser(final String providerId, final String userId, final Instant from) {
            throw new UnsupportedOperationException();
        }
    }
}
