package io.kestra.jdbc.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;

import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository.DATE_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository.FLOW_ID_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository.NAMESPACE_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository.bindableDate;
import static io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository.tenantCondition;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.KEY_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.TENANT_ID_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

/**
 * Periodically compacts the {@code execution_statistics} table.
 * <p>
 * The table mixes raw per-execution rows ({@code count = 1}) with rows already merged by a
 * previous run ({@code count = N}); see {@link ExecutionStatistic} for why every read is a
 * uniform aggregation regardless of compaction state. This job bounds row growth: for every
 * closed minute bucket that still holds raw rows, it merges them into the bucket's aggregate row
 * and deletes the raw rows.
 * <p>
 * It is only used in JDBC backends, Elasticsearch didn't compact and always aggregate on the fly when querying.
 */
@Singleton
@JdbcRepositoryEnabled
@Requires(property = "kestra.server-type", pattern = "(STANDALONE|WEBSERVER|INDEXER)")
@Slf4j
public class ExecutionStatisticsCompactor {
    // Safety cap on the number of findKeysWithRawRows() batches drained within a single tick, so
    // that a key which can never be compacted (e.g. a persistently corrupt row) can't keep the
    // batch full forever and block this thread indefinitely; the remainder is simply picked up on
    // the next tick. Not user-configurable: it exists purely to bound worst-case behavior, not to
    // be tuned.
    private static final int MAX_BATCHES_PER_TICK = 50;

    private static final Field<String> STATE_FIELD = field("state", String.class);
    private static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);

    private final io.kestra.jdbc.AbstractJdbcRepository<ExecutionStatistic> jdbcRepository;
    private final JooqDSLContextWrapper dslContextWrapper;
    private final int maxKeysPerRun;
    private final Timer compactionDurationTimer;

    @Inject
    public ExecutionStatisticsCompactor(
        @Named("executionstatistics") io.kestra.jdbc.AbstractJdbcRepository<ExecutionStatistic> jdbcRepository,
        ExecutionStatisticCompactorConfig config,
        MetricRegistry metricRegistry) {
        this.jdbcRepository = jdbcRepository;
        this.dslContextWrapper = jdbcRepository.getDslContextWrapper();
        this.maxKeysPerRun = config.maxKeysPerRun();
        this.compactionDurationTimer = metricRegistry.timer(
            MetricRegistry.METRIC_JDBC_EXECUTION_STATISTICS_COMPACTOR_DURATION,
            MetricRegistry.METRIC_JDBC_EXECUTION_STATISTICS_COMPACTOR_DURATION_DESCRIPTION
        );
    }

    // @Scheduled requires compile-time constant placeholder strings, so initialDelay/fixedDelay
    // can't be read from the injected ExecutionStatisticCompactorConfig directly; the property
    // keys and defaults here must stay in sync with that config's prefix (same pattern as
    // io.kestra.core.storages.kv.KVPurgeCleaner + KVPurgeConfiguration).
    @Scheduled(
        initialDelay = "${kestra.jdbc.execution-statistics.compactor.initial-delay:1m}",
        fixedDelay = "${kestra.jdbc.execution-statistics.compactor.fixed-delay:1m}"
    )
    public void compact() {
        compactionDurationTimer.record(this::drainBacklog);
    }

    private void drainBacklog() {
        Instant closedBefore = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        int batches = 0;
        List<BucketGroupKey> keys;
        do {
            keys = findKeysWithRawRows(closedBefore);

            for (BucketGroupKey key : keys) {
                try {
                    compactKey(key, closedBefore);
                } catch (Exception e) {
                    // one key failing (e.g. a transient deadlock) must not prevent the others from being compacted;
                    // the row(s) will simply be retried on the next tick.
                    log.warn("Unable to compact execution statistics for {}: {}", key, e.getMessage(), e);
                }
            }

            batches++;
            // a full batch means there may be more keys waiting: keep draining within this tick
            // instead of waiting for the next fixedDelay, so a burst of activity doesn't push
            // compaction further and further behind.
        } while (keys.size() == maxKeysPerRun && batches < MAX_BATCHES_PER_TICK);

        if (keys.size() == maxKeysPerRun) {
            log.warn(
                "Execution statistics compaction hit its per-tick safety cap ({} batches of {} keys); the remaining backlog will be processed on the next tick.",
                MAX_BATCHES_PER_TICK, maxKeysPerRun
            );
        }
    }

    private List<BucketGroupKey> findKeysWithRawRows(Instant closedBefore) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL
                .using(configuration)
                .selectDistinct(TENANT_ID_FIELD, NAMESPACE_FIELD, FLOW_ID_FIELD, STATE_FIELD)
                .from(jdbcRepository.getTable())
                .where(EXECUTION_ID_FIELD.isNotNull())
                .and(DATE_FIELD.lessThan(bindableDate(configuration.dialect(), closedBefore)))
                .limit(maxKeysPerRun)
                .fetch(
                    record -> new BucketGroupKey(
                        record.get(TENANT_ID_FIELD),
                        record.get(NAMESPACE_FIELD),
                        record.get(FLOW_ID_FIELD),
                        record.get(STATE_FIELD)
                    )
                )
        );
    }

    private void compactKey(BucketGroupKey key, Instant closedBefore) {
        dslContextWrapper.transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            List<ExecutionStatistic> rawRows = jdbcRepository.fetch(
                context.select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(tenantCondition(key.tenantId()))
                    .and(NAMESPACE_FIELD.eq(key.namespace()))
                    .and(FLOW_ID_FIELD.eq(key.flowId()))
                    .and(STATE_FIELD.eq(key.state()))
                    .and(EXECUTION_ID_FIELD.isNotNull())
                    .and(DATE_FIELD.lessThan(bindableDate(configuration.dialect(), closedBefore)))
                    .forUpdate()
                    .skipLocked()
            );

            if (rawRows.isEmpty()) {
                // either nothing left, or another compactor instance already claimed these rows
                return;
            }

            Map<Instant, List<ExecutionStatistic>> byBucket = rawRows.stream()
                .collect(Collectors.groupingBy(ExecutionStatistic::date));

            byBucket.forEach((bucketDate, bucketRows) -> mergeBucket(context, key, bucketDate, bucketRows));

            context.deleteFrom(jdbcRepository.getTable())
                .where(KEY_FIELD.in(rawRows.stream().map(ExecutionStatistic::executionId).toList()))
                .execute();

            log.debug("Compacted {} execution statistics for {} into {} bucket(s)", rawRows.size(), key, byBucket.size());
        });
    }

    /**
     * Merges one closed bucket's raw rows into its aggregate row.
     * <p>
     * The aggregate row is fetched-or-created with {@link io.kestra.jdbc.AbstractJdbcRepository#getOrInsert}
     * under a {@code FOR UPDATE} lock so a concurrent compactor merging the same bucket blocks
     * instead of racing (a lost-update would otherwise be possible: both merges read the same
     * pre-merge totals and the second write would silently overwrite the first).
     */
    private void mergeBucket(DSLContext context, BucketGroupKey key, Instant bucketDate, List<ExecutionStatistic> bucketRows) {
        State.Type state = State.Type.valueOf(key.state());
        ExecutionStatistic zero = new ExecutionStatistic(key.tenantId(), key.namespace(), key.flowId(), bucketDate, state, 0, 0, 0, 0, 0, 0, null, null, null);

        ExecutionStatistic existing = jdbcRepository.getOrInsert(
            context,
            () -> jdbcRepository.fetchOne(
                context.select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(KEY_FIELD.eq(zero.uid()))
                    .forUpdate()
            ),
            () -> zero
        );

        long count = 0;
        long durationSum = 0;
        long durationMin = Long.MAX_VALUE;
        long durationMax = 0;
        long taskRunCount = 0;
        long taskRunsDurationSum = 0;
        Long taskRunsDurationMin = null;
        Long taskRunsDurationMax = null;
        // bucketRows is never empty: it's a group from Collectors.groupingBy over the non-empty
        // rawRows fetched in compactKey.
        for (ExecutionStatistic row : bucketRows) {
            count += row.count();
            durationSum += row.durationSumMs();
            durationMin = Math.min(durationMin, row.durationMinMs());
            durationMax = Math.max(durationMax, row.durationMaxMs());
            taskRunCount += row.taskRunCount();
            taskRunsDurationSum += row.taskRunsDurationSumMs();
            taskRunsDurationMin = minOf(taskRunsDurationMin, row.taskRunsDurationMinMs());
            taskRunsDurationMax = maxOf(taskRunsDurationMax, row.taskRunsDurationMaxMs());
        }

        ExecutionStatistic merged = new ExecutionStatistic(
            key.tenantId(),
            key.namespace(),
            key.flowId(),
            bucketDate,
            state,
            existing.count() + count,
            existing.durationSumMs() + durationSum,
            existing.count() == 0 ? durationMin : Math.min(existing.durationMinMs(), durationMin),
            existing.count() == 0 ? durationMax : Math.max(existing.durationMaxMs(), durationMax),
            existing.taskRunCount() + taskRunCount,
            existing.taskRunsDurationSumMs() + taskRunsDurationSum,
            minOf(existing.taskRunsDurationMinMs(), taskRunsDurationMin),
            maxOf(existing.taskRunsDurationMaxMs(), taskRunsDurationMax),
            null
        );

        jdbcRepository.persist(merged, context, jdbcRepository.persistFields(merged));
    }

    /**
     * Null-safe {@code min}: a null operand means "no task run contributed a value yet", not zero.
     * Package-private: also used by {@link AbstractJdbcExecutionStatisticsRepository} for the same
     * merge semantics on the read side, instead of a second hand-rolled copy.
     */
    static Long minOf(@Nullable Long a, @Nullable Long b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        return Math.min(a, b);
    }

    /**
     * Null-safe {@code max}: a null operand means "no task run contributed a value yet", not zero.
     * Package-private: also used by {@link AbstractJdbcExecutionStatisticsRepository} for the same
     * merge semantics on the read side, instead of a second hand-rolled copy.
     */
    static Long maxOf(@Nullable Long a, @Nullable Long b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        return Math.max(a, b);
    }

    private record BucketGroupKey(String tenantId, String namespace, String flowId, String state) {
    }
}
