package io.kestra.jdbc.repository;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregations a spend ceiling is judged by, against a real database.
 *
 * <p>Worth the cost of a database rather than a mock: every count is a generated column derived from JSON, so the
 * thing most likely to be wrong is the migration's expression rather than the Java. A mocked repository would
 * agree with itself and prove nothing about either.
 */
@KestraTest
public abstract class AbstractJdbcAiUsageRepositoryTest {
    private static final String TENANT = "main";
    private static final String PROVIDER = "kestra-free-tier";

    @Inject
    protected AbstractJdbcAiUsageRepository repository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldSumEveryCountForAProvider() {
        // Given two calls on one provider and one on another
        Instant now = Instant.now();
        save(PROVIDER, "user-1", now, 1_000, 800, 50, 10);
        save(PROVIDER, "user-2", now, 2_000, 1_600, 100, 20);
        save("someone-elses-provider", "user-1", now, 9_999, 0, 9_999, 0);

        // When the provider's totals are read
        AiUsageTotals totals = repository.totals(TENANT, PROVIDER, now.minus(1, ChronoUnit.HOURS));

        // Then only that provider's calls count, and each axis is summed separately — a single total would not
        // let cached input be priced differently from cold
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
        Instant now = Instant.now();
        save(PROVIDER, "user-1", now, 1_000, 0, 100, 0);
        save(PROVIDER, "user-2", now, 5_000, 0, 500, 0);

        // When one user's totals are read
        AiUsageTotals totals = repository.totalsForUser(TENANT, PROVIDER, "user-1", now.minus(1, ChronoUnit.HOURS));

        // Then the other user's spend is not counted against them
        assertThat(totals.promptTokens()).isEqualTo(1_000);
        assertThat(totals.completionTokens()).isEqualTo(100);
    }

    @Test
    void shouldTreatUnattributedUsageAsItsOwnBucket() {
        // Given usage recorded with no user, which is every OSS turn, alongside an attributed call
        Instant now = Instant.now();
        save(PROVIDER, null, now, 700, 0, 70, 0);
        save(PROVIDER, "user-1", now, 4_000, 0, 400, 0);

        // When the unattributed bucket is read
        AiUsageTotals totals = repository.totalsForUser(TENANT, PROVIDER, null, now.minus(1, ChronoUnit.HOURS));

        // Then null is a value rather than "no filter" — otherwise an OSS install's own usage would be reported
        // as every user's, and a per-user ceiling would bind on the whole installation
        assertThat(totals.promptTokens()).isEqualTo(700);
    }

    @Test
    void shouldExcludeUsageOlderThanTheWindow() {
        // Given yesterday's spend and today's
        Instant now = Instant.now();
        save(PROVIDER, "user-1", now.minus(2, ChronoUnit.DAYS), 8_000, 0, 800, 0);
        save(PROVIDER, "user-1", now, 1_000, 0, 100, 0);

        // When a one-day window is read
        AiUsageTotals totals = repository.totals(TENANT, PROVIDER, now.minus(1, ChronoUnit.DAYS));

        // Then only today counts, which is what makes a daily ceiling reset rather than accumulate forever
        assertThat(totals.promptTokens()).isEqualTo(1_000);
    }

    @Test
    void shouldReportZeroRatherThanNothingWhenNoUsageIsRecorded() {
        // Given a provider nobody has used

        // When its totals are read
        AiUsageTotals totals = repository.totals(TENANT, "never-used", Instant.now().minus(1, ChronoUnit.DAYS));

        // Then the answer is zero, not null: SUM over no rows is null in SQL, and an installation that has spent
        // nothing has spent nothing rather than having no answer
        assertThat(totals).isEqualTo(AiUsageTotals.ZERO);
    }

    private void save(String providerId, String userId, Instant recordedAt, long prompt, long cached, long completion, long thought) {
        repository.save(new AiUsage(
            IdUtils.create(), TENANT, providerId, userId, "gemini-3.1-flash-lite", recordedAt,
            prompt, cached, completion, thought
        ));
    }
}
