package io.kestra.jdbc.repository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.utils.DateUtils;

import io.micronaut.core.annotation.Nullable;

/**
 * Base JDBC repository for the pre-aggregated {@code execution_statistics} table.
 * <p>
 * The table mixes raw rows (one per terminated execution, {@code count = 1}) with rows already
 * merged by the periodic compaction job ({@code count = N}); see {@link ExecutionStatistic} for
 * why this makes {@link #statistics} a uniform aggregation regardless of compaction state.
 * <p>
 */
public abstract class AbstractJdbcExecutionStatisticsRepository extends AbstractJdbcCrudRepository<ExecutionStatistic> implements ExecutionStatisticsRepositoryInterface {
    // Package-private: referenced by ExecutionStatisticsCompactor, which shares this table but isn't part of this class's hierarchy.
    static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);
    static final Field<String> FLOW_ID_FIELD = field("flow_id", String.class);
    static final Field<Object> DATE_FIELD = field("date");

    public AbstractJdbcExecutionStatisticsRepository(io.kestra.jdbc.AbstractJdbcRepository<ExecutionStatistic> jdbcRepository) {
        super(jdbcRepository);
    }

    /** {@inheritDoc} **/
    @Override
    protected Condition defaultFilter(String tenantId) {
        return tenantCondition(tenantId);
    }

    /** {@inheritDoc} **/
    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    /** {@inheritDoc} **/
    @Override
    public List<DailyExecutionStatistics> statistics(
        String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy) {
        DateUtils.GroupType groupByType = groupBy != null ? groupBy : DateUtils.groupByType(Duration.between(startDate, endDate));

        List<ExecutionStatistic> rows = this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<?> select = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(DATE_FIELD.greaterOrEqual(bindableDate(configuration.dialect(), startDate)))
                    .and(DATE_FIELD.lessOrEqual(bindableDate(configuration.dialect(), endDate)));

                if (namespace != null) {
                    select = select.and(NAMESPACE_FIELD.eq(namespace));
                }

                if (flowId != null) {
                    select = select.and(FLOW_ID_FIELD.eq(flowId));
                }

                return this.jdbcRepository.fetch(select);
            });

        return mapStatistics(rows, startDate, endDate, groupByType);
    }

    /** {@inheritDoc} **/
    @Override
    public List<DailyExecutionStatistics> statisticsForAllTenants(
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy) {
        DateUtils.GroupType groupByType = groupBy != null ? groupBy : DateUtils.groupByType(Duration.between(startDate, endDate));

        List<ExecutionStatistic> rows = this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<?> select = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(DATE_FIELD.greaterOrEqual(bindableDate(configuration.dialect(), startDate)))
                    .and(DATE_FIELD.lessOrEqual(bindableDate(configuration.dialect(), endDate)));

                return this.jdbcRepository.fetch(select);
            });

        return mapStatistics(rows, startDate, endDate, groupByType);
    }

    private List<DailyExecutionStatistics> mapStatistics(List<ExecutionStatistic> rows, Instant startDate, Instant endDate, DateUtils.GroupType groupByType) {
        Map<Instant, List<ExecutionStatistic>> byBucket = rows.stream()
            .collect(Collectors.groupingBy(row -> truncateToBucket(row.date(), groupByType)));

        List<DailyExecutionStatistics> results = byBucket.entrySet().stream()
            .map(entry -> dailyExecutionStatisticsMap(entry.getKey(), entry.getValue(), groupByType.val()))
            .toList();

        return fillDate(results, truncateToBucket(startDate, groupByType), truncateToBucket(endDate, groupByType), groupByType);
    }

    /**
     * Converts an absolute instant to the value type comparable with the {@code date} generated
     * column. MySQL's {@code DATETIME} generated column is populated via {@code STR_TO_DATE} on
     * the ISO-8601 string, which discards the 'Z' and stores the UTC wall-clock digits as a naive
     * (timezone-less) value; H2/PostgreSQL parse the same string into a timestamp that retains the
     * absolute instant, so an {@link java.time.OffsetDateTime} compares correctly there.
     */
    static Object bindableDate(SQLDialect dialect, Instant instant) {
        return dialect.family() == SQLDialect.MYSQL
            ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
            : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Tenant-null-safe equality condition on {@code tenant_id}.
     */
    static Condition tenantCondition(String tenantId) {
        return tenantId == null ? TENANT_ID_FIELD.isNull() : TENANT_ID_FIELD.eq(tenantId);
    }

    /**
     * Truncates an instant to the start of its bucket for the given group type. Operates directly
     * on the real (deserialized) {@link Instant}, in UTC — no SQL-side date-part extraction is
     * involved, so there is no risk of the system-default-zone reconstruction bug that a
     * digit-extraction approach (year/month/day/hour/minute columns) would be exposed to.
     */
    private static Instant truncateToBucket(Instant instant, DateUtils.GroupType groupType) {
        return switch (groupType) {
            case MINUTE -> instant.truncatedTo(ChronoUnit.MINUTES);
            case HOUR -> instant.truncatedTo(ChronoUnit.HOURS);
            case DAY -> instant.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> instant.truncatedTo(ChronoUnit.DAYS)
                .atZone(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toInstant();
            case MONTH -> instant.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }

    /**
     * Fills gaps in {@code results} so every bucket in {@code [startDate, endDate]} is present,
     * even when it has no matching rows.
     * <p>
     * {@link AbstractJdbcExecutionRepository#fillDate} and {@link AbstractJdbcMetricRepository}'s
     * private {@code fillDate} reimplement the same gap-filling idea, but anchor to
     * {@code ZoneId.systemDefault()} instead of UTC — self-consistent with their own
     * {@code AbstractJdbcRepository#getDate} bucket-key extraction (which has the same
     * system-default-zone bug), but wrong in absolute terms on non-UTC hosts. Not consolidated
     * here: fixing it properly means fixing {@code getDate()} too, which is shared by those two
     * already-shipped read paths and out of scope for this feature.
     */
    private static List<DailyExecutionStatistics> fillDate(List<DailyExecutionStatistics> results, Instant startDate, Instant endDate, DateUtils.GroupType groupByType) {
        ChronoUnit unit = switch (groupByType) {
            case MONTH -> ChronoUnit.MONTHS;
            case WEEK -> ChronoUnit.WEEKS;
            case DAY -> ChronoUnit.DAYS;
            case HOUR -> ChronoUnit.HOURS;
            case MINUTE -> ChronoUnit.MINUTES;
        };

        Map<Instant, DailyExecutionStatistics> byBucket = results.stream()
            .collect(Collectors.toMap(DailyExecutionStatistics::getStartDate, r -> r));

        List<DailyExecutionStatistics> filledResult = new ArrayList<>();
        // MONTH/WEEK are calendar (not fixed-duration) units, so advancing requires a zoned
        // representation; Instant.plus(...) only supports fixed-duration units.
        ZonedDateTime current = startDate.atZone(ZoneOffset.UTC);
        ZonedDateTime boundary = endDate.atZone(ZoneOffset.UTC).plus(1, unit);

        while (current.isBefore(boundary)) {
            Instant bucket = current.toInstant();
            filledResult.add(
                byBucket.getOrDefault(
                    bucket, DailyExecutionStatistics.builder()
                        .startDate(bucket)
                        .groupBy(groupByType.val())
                        .duration(DailyExecutionStatistics.Duration.builder().build())
                        .taskRunsDuration(DailyExecutionStatistics.Duration.builder().build())
                        .build()
                )
            );
            current = current.plus(1, unit);
        }

        return filledResult;
    }

    private static DailyExecutionStatistics dailyExecutionStatisticsMap(Instant bucket, List<ExecutionStatistic> rows, String groupByType) {
        long count = 0;
        long durationSum = 0;
        long durationMin = Long.MAX_VALUE;
        long durationMax = 0;
        long taskRunCount = 0;
        long taskRunsDurationSum = 0;
        Long taskRunsDurationMin = null;
        Long taskRunsDurationMax = null;
        Map<State.Type, Long> countsByState = new EnumMap<>(State.Type.class);

        // rows is never empty: it's a group from Collectors.groupingBy over the non-empty rows
        // fetched by statistics(), so durationMin/durationMax always get a real value below.
        for (ExecutionStatistic row : rows) {
            count += row.count();
            durationSum += row.durationSumMs();
            durationMin = Math.min(durationMin, row.durationMinMs());
            durationMax = Math.max(durationMax, row.durationMaxMs());
            taskRunCount += row.taskRunCount();
            taskRunsDurationSum += row.taskRunsDurationSumMs();
            taskRunsDurationMin = ExecutionStatisticsCompactor.minOf(taskRunsDurationMin, row.taskRunsDurationMinMs());
            taskRunsDurationMax = ExecutionStatisticsCompactor.maxOf(taskRunsDurationMax, row.taskRunsDurationMaxMs());
            countsByState.merge(row.state(), row.count(), Long::sum);
        }

        DailyExecutionStatistics build = DailyExecutionStatistics.builder()
            .startDate(bucket)
            .groupBy(groupByType)
            .duration(
                DailyExecutionStatistics.Duration.builder()
                    .avg(Duration.ofMillis(count == 0 ? 0 : durationSum / count))
                    .min(Duration.ofMillis(durationMin))
                    .max(Duration.ofMillis(durationMax))
                    .sum(Duration.ofMillis(durationSum))
                    .count(count)
                    .build()
            )
            .taskRunsDuration(
                DailyExecutionStatistics.Duration.builder()
                    .avg(Duration.ofMillis(taskRunCount == 0 ? 0 : taskRunsDurationSum / taskRunCount))
                    .min(taskRunsDurationMin == null ? null : Duration.ofMillis(taskRunsDurationMin))
                    .max(taskRunsDurationMax == null ? null : Duration.ofMillis(taskRunsDurationMax))
                    .sum(Duration.ofMillis(taskRunsDurationSum))
                    .count(taskRunCount)
                    .build()
            )
            .build();

        build.getExecutionCounts().putAll(countsByState);

        return build;
    }
}
