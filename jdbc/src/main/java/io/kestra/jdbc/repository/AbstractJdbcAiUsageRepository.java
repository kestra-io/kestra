package io.kestra.jdbc.repository;

import io.kestra.core.ai.usage.models.AiUsage;
import io.kestra.core.ai.usage.models.AiUsageTotals;
import io.kestra.core.ai.usage.repositories.AiUsageRepositoryInterface;

import io.micronaut.core.annotation.Nullable;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record4;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The token counts are generated columns, so a window's total is one {@code SUM} over an index rather than a
 * scan that deserialises every row. That matters because these queries sit on the path of a turn once limits are
 * enforced, and a turn is a dozen or more model calls.
 */
public abstract class AbstractJdbcAiUsageRepository extends AbstractJdbcCrudRepository<AiUsage>
    implements AiUsageRepositoryInterface {
    private static final Field<String> PROVIDER_ID_FIELD = field("provider_id", String.class);
    private static final Field<String> USER_ID_FIELD = field("user_id", String.class);
    private static final Field<OffsetDateTime> RECORDED_AT_FIELD = field("recorded_at", OffsetDateTime.class);

    private static final Field<Long> PROMPT_TOKENS_FIELD = field("prompt_tokens", Long.class);
    private static final Field<Long> CACHED_PROMPT_TOKENS_FIELD = field("cached_prompt_tokens", Long.class);
    private static final Field<Long> COMPLETION_TOKENS_FIELD = field("completion_tokens", Long.class);
    private static final Field<Long> THOUGHT_TOKENS_FIELD = field("thought_tokens", Long.class);

    public AbstractJdbcAiUsageRepository(io.kestra.jdbc.AbstractJdbcRepository<AiUsage> jdbcRepository) {
        super(jdbcRepository);
    }

    /**
     * Tenant isolation without the soft-delete predicate the base class adds.
     *
     * <p>A usage row is a record of money already spent, so it is never deleted or superseded — the table has no
     * {@code deleted} column, and the inherited filter would query one that does not exist.
     */
    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.noCondition();
    }

    @Override
    public AiUsageTotals totals(String tenant, String providerId, Instant from) {
        return sum(tenant, PROVIDER_ID_FIELD.eq(providerId).and(recordedSince(from)));
    }

    @Override
    public AiUsageTotals totalsForUser(String tenant, String providerId, @Nullable String userId, Instant from) {
        return sum(
            tenant,
            PROVIDER_ID_FIELD.eq(providerId)
                // Null is a real value here, not a missing filter: OSS records usage with no user at all, and
                // those rows are exactly the ones a null query should return.
                .and(eqOrIsNull(USER_ID_FIELD, userId))
                .and(recordedSince(from))
        );
    }

    private Condition recordedSince(Instant from) {
        return RECORDED_AT_FIELD.greaterOrEqual(from.atOffset(ZoneOffset.UTC));
    }

    /**
     * One round trip for all four counts.
     *
     * <p>{@code SUM} over no rows is null rather than zero, so each is coalesced — an installation that has
     * recorded nothing yet has spent nothing, which is not the same as having no answer.
     */
    private AiUsageTotals sum(String tenant, Condition condition) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Record4<Long, Long, Long, Long> summed = context
                    .select(
                        DSL.coalesce(DSL.sum(PROMPT_TOKENS_FIELD), DSL.zero()).cast(Long.class),
                        DSL.coalesce(DSL.sum(CACHED_PROMPT_TOKENS_FIELD), DSL.zero()).cast(Long.class),
                        DSL.coalesce(DSL.sum(COMPLETION_TOKENS_FIELD), DSL.zero()).cast(Long.class),
                        DSL.coalesce(DSL.sum(THOUGHT_TOKENS_FIELD), DSL.zero()).cast(Long.class)
                    )
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenant))
                    .and(condition)
                    .fetchOne();

                return summed == null
                    ? AiUsageTotals.ZERO
                    : new AiUsageTotals(summed.value1(), summed.value2(), summed.value3(), summed.value4());
            });
    }
}
