package io.kestra.jdbc.repository;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.*;
import static io.kestra.jdbc.repository.AbstractJdbcTaskRunStatisticsRepository.*;

/**
 * Periodically compacts the {@code task_run_statistics} table.
 * <p>
 * The table mixes raw per-raskrun rows ({@code count = 1}) with rows already merged by a
 * previous run ({@code count = N}); see {@link TaskRun} for why every read is a
 * uniform aggregation regardless of compaction state. This job bounds row growth: for every
 * closed minute bucket that still holds raw rows, it merges them into the bucket's aggregate row
 * and deletes the raw rows.
 * <p>
 * It is only used in JDBC backends, Elasticsearch didn't compact and always aggregate on the fly when querying.
 */
@Singleton
@JdbcRepositoryEnabled
@Requires(property = "kestra.server-type", pattern = "(STANDALONE|WEBSERVER|INDEXER)")
@Requires(property = "kestra.task-run-statistics.enabled", value = "true", defaultValue = "false")
@Slf4j
public class TaskRunStatisticsCompactor {
    // Safety cap on the number of findKeysWithRawRows() batches drained within a single tick, so
    // that a key which can never be compacted (e.g. a persistently corrupt row) can't keep the
    // batch full forever and block this thread indefinitely; the remainder is simply picked up on
    // the next tick. Not user-configurable: it exists purely to bound worst-case behavior, not to
    // be tuned.
    private static final int MAX_BATCHES_PER_TICK = 50;

    private static final Field<String> STATE_FIELD = field("state", String.class);
    private static final Field<String> TASK_ID_FIELD = field("task_id", String.class);      // Specific to task runs
    private static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    private static final Field<String> TASK_RUN_ID_FIELD = field("task_run_id", String.class);

    private final io.kestra.jdbc.AbstractJdbcRepository<TaskRunStatistic> jdbcRepository;
    private final JooqDSLContextWrapper dslContextWrapper;
    private final int maxKeysPerRun;
    private final Timer compactionDurationTimer;

    public TaskRunStatisticsCompactor(
        @Named("taskrunstatistics") io.kestra.jdbc.AbstractJdbcRepository<TaskRunStatistic> jdbcRepository,
        TaskRunStatisticCompactorConfig config,
        MetricRegistry metricRegistry) {
        this.jdbcRepository = jdbcRepository;
        this.dslContextWrapper = jdbcRepository.getDslContextWrapper();
        this.maxKeysPerRun = config.maxKeysPerRun();
        this.compactionDurationTimer = metricRegistry.timer(
            MetricRegistry.METRIC_JDBC_TASKRUN_STATISTICS_COMPACTOR_DURATION,
            MetricRegistry.METRIC_JDBC_TASKRUN_STATISTICS_COMPACTOR_DURATION_DESCRIPTION
        );
    }

    // @Scheduled requires compile-time constant placeholder strings, so initialDelay/fixedDelay
    // can't be read from the injected TaskRunStatisticCompactorConfig directly; the property
    // keys and defaults here must stay in sync with that config's prefix (same pattern as
    // io.kestra.core.storages.kv.KVPurgeCleaner + KVPurgeConfiguration).
    @Scheduled(
        initialDelay = "${kestra.jdbc.task-run-statistics.compactor.initial-delay:1m}",
        fixedDelay = "${kestra.jdbc.task-run-statistics-statistics.compactor.fixed-delay:1m}"
    )
    public void compact() {
        compactionDurationTimer.record(this::drainBacklog);
    }

    private void drainBacklog() {
        Instant closedBefore = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        int batches = 0;
        List<TaskRunStatisticsCompactor.BucketGroupKey> keys;
        do {
            keys = findKeysWithRawRows(closedBefore);

            for (TaskRunStatisticsCompactor.BucketGroupKey key : keys) {
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

    private List<TaskRunStatisticsCompactor.BucketGroupKey> findKeysWithRawRows(Instant closedBefore) {
        return dslContextWrapper.transactionResult(
            configuration -> DSL
                .using(configuration)
                .selectDistinct(
                    TENANT_ID_FIELD,
                    NAMESPACE_FIELD,
                    FLOW_ID_FIELD,
                    TASK_ID_FIELD,
                    STATE_FIELD
                )
                .from(jdbcRepository.getTable())
                .where(TASK_RUN_ID_FIELD.isNotNull())
                .and(DATE_FIELD.lessThan(bindableDate(configuration.dialect(), closedBefore)))
                .limit(maxKeysPerRun)
                .fetch(
                    record -> new TaskRunStatisticsCompactor.BucketGroupKey(
                        record.get(TENANT_ID_FIELD),
                        record.get(NAMESPACE_FIELD),
                        record.get(FLOW_ID_FIELD),
                        record.get(TASK_ID_FIELD),
                        record.get(STATE_FIELD)
                    )
                )
        );
    }

    private void compactKey(BucketGroupKey key, Instant closedBefore) {
        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            // 1. Fetch raw uncompacted rows for this key with FOR UPDATE SKIP LOCKED
            List<TaskRunStatistic> rawRows = jdbcRepository.fetch(
                context.select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(tenantCondition(key.tenantId()))
                    .and(NAMESPACE_FIELD.eq(key.namespace()))
                    .and(FLOW_ID_FIELD.eq(key.flowId()))
                    .and(TASK_ID_FIELD.eq(key.taskId()))
                    .and(STATE_FIELD.eq(key.state()))
                    .and(TASK_RUN_ID_FIELD.isNotNull())
                    .and(DATE_FIELD.lessThan(bindableDate(configuration.dialect(), closedBefore)))
                    .forUpdate()
                    .skipLocked()
            );

            // Either nothing left to compact, or another compactor instance locked these rows
            if (rawRows.isEmpty()) {
                return;
            }

            // 2. Group raw rows by minute date bucket
            Map<Instant, List<TaskRunStatistic>> byBucket = rawRows.stream()
                .collect(Collectors.groupingBy(TaskRunStatistic::date));

            // 3. Merge each minute bucket
            byBucket.forEach((bucketDate, bucketRows) -> mergeBucket(context, key, bucketDate, bucketRows));

            // 4. Delete the processed raw rows by their primary key (uid)
            context.deleteFrom(jdbcRepository.getTable())
                .where(KEY_FIELD.in(rawRows.stream().map(TaskRunStatistic::uid).toList()))
                .execute();

            log.debug("Compacted {} task run statistics for {} into {} bucket(s)", rawRows.size(), key, byBucket.size());
        });
    }

    /**
     * Merges raw rows for a closed minute bucket into a single aggregated TaskRunStatistic row.
     */
    private void mergeBucket(
        DSLContext context,
        BucketGroupKey key,
        Instant bucketDate,
        List<TaskRunStatistic> bucketRows
    ) {
        State.Type state = State.Type.valueOf(key.state());

        // 1. Define a zero-state template row used to look up or insert the aggregate bucket row
        TaskRunStatistic zero = new TaskRunStatistic(
            key.tenantId(),
            key.namespace(),
            key.flowId(),
            key.taskId(),
            bucketDate,
            state,
            0L,
            0L,
            0L,
            0L,
            null, // executionId is null for aggregated bucket summary
            null  // taskRunId is null for aggregated bucket summary
        );

        // 2. Lock or insert the existing aggregated row
        TaskRunStatistic existing = jdbcRepository.getOrInsert(
            context,
            () -> jdbcRepository.fetchOne(
                context.select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(KEY_FIELD.eq(zero.uid()))
                    .forUpdate()
            ),
            () -> zero
        );

        // 3. Aggregate metrics across all raw rows in this bucket
        long count = 0;
        long durationSum = 0;
        long durationMin = Long.MAX_VALUE;
        long durationMax = 0;

        for (TaskRunStatistic row : bucketRows) {
            count += row.count();
            durationSum += row.durationSumMs();
            durationMin = Math.min(durationMin, row.durationMinMs());
            durationMax = Math.max(durationMax, row.durationMaxMs());
        }

        // 4. Build the merged TaskRunStatistic aggregate record
        TaskRunStatistic merged = new TaskRunStatistic(
            key.tenantId(),
            key.namespace(),
            key.flowId(),
            key.taskId(),
            bucketDate,
            state,
            existing.count() + count,
            existing.durationSumMs() + durationSum,
            existing.count() == 0 ? durationMin : Math.min(existing.durationMinMs(), durationMin),
            existing.count() == 0 ? durationMax : Math.max(existing.durationMaxMs(), durationMax),
            null, // executionId = null identifies an aggregated row
            null  // taskRunId = null identifies an aggregated row
        );

        // 5. Persist updated aggregate record back to database
        jdbcRepository.persist(merged, context, jdbcRepository.persistFields(merged));
    }

    private record BucketGroupKey(
        String tenantId,
        String namespace,
        String flowId,
        String taskId,
        String state
    ) {
    }
}

