package io.kestra.jdbc.repository;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.*;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.TaskRunStatisticRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import org.jooq.SQLDialect;
import java.time.LocalDateTime;
import io.micronaut.core.annotation.Nullable;

/**
 * Base JDBC repository for the pre-aggregated {@code task_run_statistics} table.
 * <p>
 * The table mixes raw rows (one per terminated task run, {@code count = 1}) with rows already
 * merged by the periodic compaction job ({@code count = N});
 */
public abstract class AbstractJdbcTaskRunStatisticsRepository
    extends AbstractJdbcCrudRepository<TaskRunStatistic>
    implements TaskRunStatisticRepositoryInterface {

    static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);
    static final Field<String> FLOW_ID_FIELD = field("flow_id", String.class);
    static final Field<String> TASK_ID_FIELD = field("task_id", String.class);
    static final Field<Object> DATE_FIELD = field("date");

    public AbstractJdbcTaskRunStatisticsRepository(io.kestra.jdbc.AbstractJdbcRepository<TaskRunStatistic> jdbcRepository) {
        super(jdbcRepository);
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

    /** {@inheritDoc} **/
    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    /** {@inheritDoc} **/
    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    /**
     * Tenant-null-safe equality condition on {@code tenant_id}.
     */
    static Condition tenantCondition(String tenantId) {
        return tenantId == null ? TENANT_ID_FIELD.isNull() : TENANT_ID_FIELD.eq(tenantId);
    }

    /** {@inheritDoc} **/
    @Override
    public List<DailyExecutionStatistics> statistics(
        String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable String taskId,
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy) {

        DateUtils.GroupType groupByType = groupBy != null
            ? groupBy
            : DateUtils.groupByType(Duration.between(startDate, endDate));

        List<TaskRunStatistic> rows = this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
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

                if (taskId != null) {
                    select = select.and(TASK_ID_FIELD.eq(taskId));
                }

                return this.jdbcRepository.fetch(select);
            });

        Map<Instant, List<TaskRunStatistic>> byBucket = rows.stream()
            .collect(Collectors.groupingBy(row -> truncateToBucket(row.date(), groupByType)));

        List<DailyExecutionStatistics> results = byBucket.entrySet().stream()
            .map(entry -> dailyExecutionStatisticsMap(entry.getKey(), entry.getValue(), groupByType.val()))
            .toList();

        return fillDate(results, truncateToBucket(startDate, groupByType), truncateToBucket(endDate, groupByType), groupByType);
    }

    /**
     * Truncates an instant to the start of its bucket for the given group type.
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
     */
    private static List<DailyExecutionStatistics> fillDate(
        List<DailyExecutionStatistics> results,
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupByType) {

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
        ZonedDateTime current = startDate.atZone(ZoneOffset.UTC);
        ZonedDateTime boundary = endDate.atZone(ZoneOffset.UTC).plus(1, unit);

        while (current.isBefore(boundary)) {
            Instant bucket = current.toInstant();
            filledResult.add(byBucket.getOrDefault(bucket, DailyExecutionStatistics.builder()
                .startDate(bucket)
                .groupBy(groupByType.val())
                .duration(DailyExecutionStatistics.Duration.builder().build())
                .build()));
            current = current.plus(1, unit);
        }

        return filledResult;
    }

    private static DailyExecutionStatistics dailyExecutionStatisticsMap(Instant bucket, List<TaskRunStatistic> rows, String groupByType) {
        long durationSum = rows.stream().mapToLong(TaskRunStatistic::durationSumMs).sum();
        long count = rows.stream().mapToLong(TaskRunStatistic::count).sum();

        DailyExecutionStatistics build = DailyExecutionStatistics.builder()
            .startDate(bucket)
            .groupBy(groupByType)
            .duration(
                DailyExecutionStatistics.Duration.builder()
                    .avg(Duration.ofMillis(count == 0 ? 0 : durationSum / count))
                    .min(rows.stream().mapToLong(TaskRunStatistic::durationMinMs).min().stream().mapToObj(Duration::ofMillis).findFirst().orElse(null))
                    .max(rows.stream().mapToLong(TaskRunStatistic::durationMaxMs).max().stream().mapToObj(Duration::ofMillis).findFirst().orElse(null))
                    .sum(Duration.ofMillis(durationSum))
                    .count(count)
                    .build()
            )
            .build();

        Map<State.Type, Long> countsByState = rows.stream()
            .collect(Collectors.groupingBy(TaskRunStatistic::state, Collectors.summingLong(TaskRunStatistic::count)));
        build.getExecutionCounts().putAll(countsByState);

        return build;
    }
}
