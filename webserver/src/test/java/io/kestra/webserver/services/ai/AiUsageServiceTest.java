package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.ai.usage.repositories.AiUsageRepositoryInterface;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Recording and ceilings, on the two axes the spec asks for: the installation and the caller.
 *
 * <p>Real providers and the real store throughout: each ceiling below is declared the way an operator declares
 * one, and the totals it is judged against are summed by H2 from rows this service wrote. A stubbed repository
 * would agree with itself about both, while the parts most likely to be wrong are the migration's generated
 * columns and the binding of a limit out of configuration.
 *
 * <p>Every scenario owns a provider id, since the tests share one database and totals are read per provider.
 */
@KestraTest
@Property(name = "kestra.ai.providers[0].id", value = AiUsageServiceTest.NO_CEILING)
@Property(name = "kestra.ai.providers[0].type", value = "gemini")
@Property(name = "kestra.ai.providers[0].is-default", value = "true")
@Property(name = "kestra.ai.providers[0].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[1].id", value = AiUsageServiceTest.INSTALLATION_CEILING)
@Property(name = "kestra.ai.providers[1].type", value = "gemini")
@Property(name = "kestra.ai.providers[1].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.enabled", value = "true")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.max-weight", value = "10000")
@Property(name = "kestra.ai.providers[2].id", value = AiUsageServiceTest.PER_USER_CEILING)
@Property(name = "kestra.ai.providers[2].type", value = "gemini")
@Property(name = "kestra.ai.providers[2].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[2].configuration.usage-limit.enabled", value = "true")
@Property(name = "kestra.ai.providers[2].configuration.usage-limit.max-weight", value = "1000000")
@Property(name = "kestra.ai.providers[2].configuration.usage-limit.user-max-weight", value = "5000")
@Property(name = "kestra.ai.providers[3].id", value = AiUsageServiceTest.UNATTRIBUTED_SPEND)
@Property(name = "kestra.ai.providers[3].type", value = "gemini")
@Property(name = "kestra.ai.providers[3].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[3].configuration.usage-limit.enabled", value = "true")
@Property(name = "kestra.ai.providers[3].configuration.usage-limit.max-weight", value = "1000")
@Property(name = "kestra.ai.providers[3].configuration.usage-limit.user-max-weight", value = "100")
@Property(name = "kestra.ai.providers[4].id", value = AiUsageServiceTest.SHORT_WINDOW)
@Property(name = "kestra.ai.providers[4].type", value = "gemini")
@Property(name = "kestra.ai.providers[4].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[4].configuration.usage-limit.enabled", value = "true")
@Property(name = "kestra.ai.providers[4].configuration.usage-limit.max-weight", value = "1000")
@Property(name = "kestra.ai.providers[4].configuration.usage-limit.window", value = "P7D")
class AiUsageServiceTest {
    static final String NO_CEILING = "gemini-without-a-ceiling";
    static final String INSTALLATION_CEILING = "gemini-with-an-installation-ceiling";
    static final String PER_USER_CEILING = "gemini-with-a-per-user-ceiling";
    static final String UNATTRIBUTED_SPEND = "gemini-metering-unattributed-spend";
    static final String SHORT_WINDOW = "gemini-with-a-seven-day-window";

    private static final String TENANT = TenantService.MAIN_TENANT;
    private static final String MODEL = "gemini-3.1-flash-lite";

    @Inject
    AiUsageService service;

    @Inject
    AiUsageRepositoryInterface usageStore;

    @Inject
    AiServiceManager aiServiceManager;

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
        AiUsageTotals totals = usageStore.totals(TENANT, provider, windowStart);
        assertThat(totals.promptTokens()).isEqualTo(1_000);
        assertThat(totals.cachedPromptTokens()).isEqualTo(400);
        assertThat(totals.completionTokens()).isEqualTo(19);
        assertThat(totals.thoughtTokens()).isEqualTo(128);
        // and attributed to the caller, which is what the per-user axis is later summed by
        assertThat(usageStore.totalsForUser(TENANT, provider, "user-1", windowStart).promptTokens()).isEqualTo(1_000);
    }

    @Test
    void shouldRecordUnderTheDefaultProviderWhenTheCallerNamedNone() {
        // Given a caller that left the provider unnamed, as the chat endpoint allows
        Instant windowStart = Instant.now().minus(Duration.ofHours(1));
        AiUsageTotals before = usageStore.totals(TENANT, aiServiceManager.getDefaultProviderId(), windowStart);

        // When
        service.record(TENANT, null, null, MODEL, new TokenUsage(10, 5, 15));

        // Then the row names the provider the turn actually ran against. Recording it as "none" would split one
        // provider's history across two keys, and its ceiling would then see only half its own spend.
        AiUsageTotals after = usageStore.totals(TENANT, aiServiceManager.getDefaultProviderId(), windowStart);
        assertThat(after.promptTokens() - before.promptTokens()).isEqualTo(10);
        assertThat(after.completionTokens() - before.completionTokens()).isEqualTo(5);
    }

    @Test
    void shouldWriteNothingWhenTheProviderReportedNoUsage() {
        // Given a call the provider reported no counts for — a turn that failed mid-stream
        String provider = uniqueProvider();
        service.record(TENANT, provider, null, null, null);

        // Then no row is written, rather than a row of zeroes that adds nothing but rows
        assertThat(usageStore.totals(TENANT, provider, Instant.now().minus(Duration.ofHours(1))))
            .isEqualTo(AiUsageTotals.ZERO);
    }

    @Test
    void shouldNotFailTheTurnWhenRecordingFails() {
        // Given a store that cannot be written to, which the real one cannot be asked to be on demand
        AiUsageService unrecordable =
            new AiUsageService(new UnwritableAiUsageRepository(), aiServiceManager, new AiTokenUsageReader());

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
        AiUsageStatus status = service.status(TENANT, NO_CEILING, "user-1");

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
        AiUsageStatus status = service.status(TENANT, INSTALLATION_CEILING, null);

        // Then the ceiling binds on weight, not on raw tokens: 2,600 tokens are under any raw reading of a
        // 10,000 limit, while their cost is over it. That difference is the reason weights exist.
        assertThat(status.global().weight()).isEqualTo(10_600);
        assertThat(status.isExceeded()).isTrue();
        assertThat(status.exceededMessage()).contains("This installation has reached");
    }

    @Test
    void shouldRefuseTheCallerAloneWhenOnlyTheirOwnCeilingIsReached() {
        // Given plenty of installation-wide allowance but a small per-user one, one user who has spent theirs,
        // and as much again that is nobody's
        service.record(TENANT, PER_USER_CEILING, "user-1", MODEL, new TokenUsage(5_000, 0, 5_000));
        service.record(TENANT, PER_USER_CEILING, null, MODEL, new TokenUsage(5_000, 0, 5_000));

        // When
        AiUsageStatus status = service.status(TENANT, PER_USER_CEILING, "user-1");

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
        AiUsageStatus status = service.status(TENANT, UNATTRIBUTED_SPEND, null);

        // Then the per-user axis is not evaluated at all. Querying it with a null user would return every
        // unattributed row — that is, the whole installation — so a 100-token personal ceiling would lock the
        // install out at the same moment its 1,000-token one did.
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
        assertThat(service.status(TENANT, "nope", null).enabled()).isFalse();
    }

    @Test
    void shouldCountOnlyTheConfiguredWindowRatherThanAllHistory() {
        // Given a seven-day window, spend from ten days ago that would exhaust the ceiling on its own...
        usageStore.save(new AiUsage(
            IdUtils.create(), TENANT, SHORT_WINDOW, null, MODEL, Instant.now().minus(Duration.ofDays(10)),
            5_000, 0, 0, 0
        ));
        // ...and a little spend inside the window
        service.record(TENANT, SHORT_WINDOW, null, MODEL, new TokenUsage(100, 0, 100));

        // When
        Instant before = Instant.now();
        AiUsageStatus status = service.status(TENANT, SHORT_WINDOW, null);

        // Then only what falls inside the window counts, so a ceiling frees up as the window moves. Summing all
        // history instead would make the first exhaustion permanent.
        assertThat(status.windowStart())
            .isBetween(before.minus(Duration.ofDays(7)).minusSeconds(5), before.minus(Duration.ofDays(7)).plusSeconds(5));
        assertThat(status.global().weight()).isEqualTo(100);
        assertThat(status.global().maxWeight()).isEqualTo(1_000);
        assertThat(status.isExceeded()).isFalse();
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
        public AiUsageTotals totals(final String tenant, final String providerId, final Instant from) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiUsageTotals totalsForUser(final String tenant, final String providerId, final String userId, final Instant from) {
            throw new UnsupportedOperationException();
        }
    }
}
