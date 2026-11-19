package io.kestra.core.ai.usage.repositories;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregations a spend ceiling is judged by, run against whichever store a deployment uses.
 *
 * <p>One contract for every backend: the JDBC and ElasticSearch implementations express the same questions in
 * nothing alike — a null column and a {@code coalesce} against a {@code mustNot exists} clause — so only a
 * shared test says they answer the same.
 *
 * <p>Each case owns a provider id rather than clearing the store, since ElasticSearch has no cheap equivalent
 * of dropping a table and neither axis is scoped by tenant.
 *
 * <p>Carries no test annotation of its own: JDBC runs these under {@code @KestraTest} and ElasticSearch under
 * {@code @MicronautTest}, which must not both apply.
 */
public abstract class AbstractAiUsageRepositoryTest {
    private static final String MODEL = "gemini-3.1-flash-lite";

    @Inject
    protected AiUsageRepositoryInterface repository;

    @Test
    void shouldSumEveryCountForAProvider() {
        // Given two calls on one provider and one on another
        String tenant = IdUtils.create();
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(tenant, provider, "user-1", now, 1_000, 800, 50, 10);
        save(tenant, provider, "user-2", now, 2_000, 1_600, 100, 20);
        save(tenant, IdUtils.create(), "user-1", now, 9_999, 0, 9_999, 0);

        // When the provider's totals are read
        AiUsageTotals totals = repository.totals(provider, now.minus(1, ChronoUnit.HOURS));

        // Then only that provider's calls count, each axis summed separately so cached input can be priced
        // differently from cold
        assertThat(totals.promptTokens()).isEqualTo(3_000);
        assertThat(totals.cachedPromptTokens()).isEqualTo(2_400);
        assertThat(totals.completionTokens()).isEqualTo(150);
        assertThat(totals.thoughtTokens()).isEqualTo(30);
        assertThat(totals.coldPromptTokens()).isEqualTo(600);
        assertThat(totals.outputTokens()).isEqualTo(180);
    }

    @Test
    void shouldNarrowToOneUser() {
        // Given two users on the same provider
        String tenant = IdUtils.create();
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(tenant, provider, "user-1", now, 1_000, 0, 100, 0);
        save(tenant, provider, "user-2", now, 5_000, 0, 500, 0);

        // When one user's totals are read
        AiUsageTotals totals = repository.totalsForUser(provider, "user-1", now.minus(1, ChronoUnit.HOURS));

        // Then the other user's spend is not counted against them
        assertThat(totals.promptTokens()).isEqualTo(1_000);
        assertThat(totals.completionTokens()).isEqualTo(100);
    }

    @Test
    void shouldCountAUsersSpendAcrossEveryTenant() {
        // Given one user spending under two tenants, as a user with access to both does
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(IdUtils.create(), provider, "user-1", now, 1_000, 0, 100, 0);
        save(IdUtils.create(), provider, "user-1", now, 3_000, 0, 300, 0);
        save(IdUtils.create(), provider, "user-2", now, 9_999, 0, 999, 0);

        // When that user's totals are read
        AiUsageTotals totals = repository.totalsForUser(provider, "user-1", now.minus(1, ChronoUnit.HOURS));

        // Then both tenants count against the one allowance: scoping by tenant would hand the same person a
        // fresh allowance in each
        assertThat(totals.promptTokens()).isEqualTo(4_000);
        assertThat(totals.completionTokens()).isEqualTo(400);
    }

    @Test
    void shouldTreatUnattributedUsageAsItsOwnBucket() {
        // Given unattributed usage alongside an attributed call
        String tenant = IdUtils.create();
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(tenant, provider, null, now, 700, 0, 70, 0);
        save(tenant, provider, "user-1", now, 4_000, 0, 400, 0);

        // When the unattributed bucket is read
        AiUsageTotals totals = repository.totalsForUser(provider, null, now.minus(1, ChronoUnit.HOURS));

        // Then null is a value rather than "no filter", which would report every user's spend instead
        assertThat(totals.promptTokens()).isEqualTo(700);
    }

    @Test
    void shouldCountEveryTenantsSpend() {
        // Given the same provider used under two tenants, and under none at all
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(IdUtils.create(), provider, "user-1", now, 1_000, 0, 100, 0);
        save(IdUtils.create(), provider, "user-1", now, 6_000, 0, 600, 0);
        save(null, provider, "user-1", now, 300, 0, 30, 0);

        // When the provider's totals are read
        AiUsageTotals totals = repository.totals(provider, now.minus(1, ChronoUnit.HOURS));

        // Then every tenant counts against the one ceiling, including rows recorded without one: the key is
        // billed to the installation as a single bill
        assertThat(totals.promptTokens()).isEqualTo(7_300);
        assertThat(totals.completionTokens()).isEqualTo(730);
    }

    @Test
    void shouldExcludeUsageOlderThanTheWindow() {
        // Given yesterday's spend and today's
        String tenant = IdUtils.create();
        String provider = IdUtils.create();
        Instant now = Instant.now();
        save(tenant, provider, "user-1", now.minus(2, ChronoUnit.DAYS), 8_000, 0, 800, 0);
        save(tenant, provider, "user-1", now, 1_000, 0, 100, 0);

        // When a one-day window is read
        AiUsageTotals totals = repository.totals(provider, now.minus(1, ChronoUnit.DAYS));

        // Then only today counts, which is what makes a daily ceiling reset
        assertThat(totals.promptTokens()).isEqualTo(1_000);
    }

    @Test
    void shouldReportZeroRatherThanNothingWhenNoUsageIsRecorded() {
        // Given a provider nobody has used

        // When its totals are read
        AiUsageTotals totals = repository.totals("never-used", Instant.now().minus(1, ChronoUnit.DAYS));

        // Then the answer is zero, not null and not NaN — an aggregate over nothing is null in SQL and an
        // empty sum in ElasticSearch
        assertThat(totals).isEqualTo(AiUsageTotals.ZERO);
    }

    protected void save(String tenant, String providerId, String userId, Instant recordedAt, long prompt, long cached, long completion, long thought) {
        repository.save(new AiUsage(
            IdUtils.create(), tenant, providerId, userId, MODEL, recordedAt,
            prompt, cached, completion, thought
        ));
    }
}
