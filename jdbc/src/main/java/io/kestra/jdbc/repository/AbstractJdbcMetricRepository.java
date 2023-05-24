package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregation;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.micrometer.common.lang.Nullable;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Singleton
public abstract class AbstractJdbcMetricRepository extends AbstractJdbcRepository implements MetricRepositoryInterface, JdbcIndexerInterface<MetricEntry> {
    protected io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository;

    public AbstractJdbcMetricRepository(io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionId(String id, Pageable pageable) {
        return this.query(
            field("execution_id").eq(id)
            , pageable
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Pageable pageable) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            pageable
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Pageable pageable) {
        return this.query(
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            pageable
        );
    }

    @Override
    public List<String> flowMetrics(
        String namespace,
        String flowId
    ) {
        return this.queryDistinct(
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace)),
            "metric_name"
        );
    }

    @Override
    public List<String> taskMetrics(
        String namespace,
        String flowId,
        String taskId
    ) {
        return this.queryDistinct(
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace))
                .and(field("task_id").eq(taskId)),
            "metric_name"
        );
    }

    @Override
    public List<String> tasksWithMetrics(
        String namespace,
        String flowId
    ) {
        return this.queryDistinct(
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace)),
            "task_id"
        );
    }

    @Override
    public MetricAggregations aggregateByFlowId(
        String namespace,
        String flowId,
        @Nullable String taskId,
        String metric,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        String aggregation
    ) {
        Condition conditions = field("flow_id").eq(flowId)
            .and(field("namespace").eq(namespace))
            .and(field("metric_name").eq(metric));
        if (taskId != null) {
            conditions = conditions.and(field("task_id").eq(taskId));
        }
        return MetricAggregations
            .builder()
            .aggregations(
                this.aggregate(
                    conditions,
                    startDate,
                    endDate,
                    aggregation
                ))
            .groupBy(DateUtils.groupByType(Duration.between(startDate, endDate).toDays()).val())
            .build();
    }

    @Override
    public MetricEntry save(MetricEntry metric) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(metric);
        this.jdbcRepository.persist(metric, fields);

        return metric;
    }

    @Override
    public Integer purge(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                return context.delete(this.jdbcRepository.getTable())
                    .where(field("execution_id", String.class).eq(execution.getId()))
                    .execute();
            });
    }

    @Override
    public MetricEntry save(DSLContext dslContext, MetricEntry metric) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(metric);
        this.jdbcRepository.persist(metric, dslContext, fields);

        return metric;
    }

    private List<String> queryDistinct(Condition condition, String field) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .selectDistinct(field(field))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                select = select.and(condition);

                return select.fetch().map(record -> record.get(field, String.class));
            });
    }

    private ArrayListTotal<MetricEntry> query(Condition condition, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                select = select.and(condition);

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    private List<MetricAggregation> aggregate(
        Condition condition,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        String aggregation
    ) {
        List<Field<?>> dateFields = new ArrayList<>(groupByFields(Duration.between(startDate, endDate).toDays()));
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(dateFields)
                    .select(
                        field("metric_name"),
                        aggregate(aggregation)
                    )
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                select = select.and(condition);

                if (startDate != null) {
                    select = select.and(field("timestamp").greaterOrEqual(startDate.toOffsetDateTime()));
                }

                if (endDate != null) {
                    select = select.and(field("timestamp").lessOrEqual(endDate.toOffsetDateTime()));
                }

                dateFields.add(field("metric_name"));

                var selectGroup = select.groupBy(dateFields);

                List<MetricAggregation> result = this.jdbcRepository
                    .fetchMetricStat(selectGroup, DateUtils.groupByType(Duration.between(startDate, endDate).toDays()).val());

                List<MetricAggregation> fillResult = fillDate(result, startDate, endDate);

                return fillResult;
            });
    }

    private Field<?> aggregate(String aggregation) {
        return switch (aggregation) {
            case "avg" -> DSL.avg(field("metric_value", Double.class)).as("metric_value");
            case "sum" -> DSL.sum(field("metric_value", Double.class)).as("metric_value");
            case "min" -> DSL.min(field("metric_value", Double.class)).as("metric_value");
            case "max" -> DSL.max(field("metric_value", Double.class)).as("metric_value");
            default -> throw new IllegalArgumentException("Invalid aggregation: " + aggregation);
        };
    }

    private List<Field<?>> groupByFields(Long dayCount) {
        Field<Integer> month = DSL.month(DSL.timestamp(field("timestamp", Date.class))).as("month");
        Field<Integer> year = DSL.year(DSL.timestamp(field("timestamp", Date.class))).as("year");
        Field<Integer> day = DSL.day(DSL.timestamp(field("timestamp", Date.class))).as("day");
        Field<Integer> week = DSL.week(DSL.timestamp(field("timestamp", Date.class))).as("week");
        Field<Integer> hour = DSL.hour(DSL.timestamp(field("timestamp", Date.class))).as("hour");

        if (dayCount > 365) {
            return List.of(year, month);
        } else if (dayCount > 180) {
            return List.of(year, week);
        } else if (dayCount > 1) {
            return List.of(year, month, day);
        } else {
            return List.of(year, month, day, hour);
        }
    }

    private List<MetricAggregation> fillDate(List<MetricAggregation> result, ZonedDateTime startDate, ZonedDateTime endDate) {
        DateUtils.GroupType groupByType = DateUtils.groupByType(Duration.between(startDate, endDate).toDays());

        if (groupByType.equals(DateUtils.GroupType.MONTH)) {
            return fillDate(result, startDate, endDate, ChronoUnit.MONTHS, "YYYY-MM");
        } else if (groupByType.equals(DateUtils.GroupType.WEEK)) {
            return fillDate(result, startDate, endDate, ChronoUnit.WEEKS, "YYYY-ww");
        } else if (groupByType.equals(DateUtils.GroupType.DAY)) {
            return fillDate(result, startDate, endDate, ChronoUnit.DAYS, "YYYY-MM-DD");
        } else {
            return fillDate(result, startDate, endDate, ChronoUnit.HOURS, "YYYY-MM-DD HH");
        }
    }

    private List<MetricAggregation> fillDate(
        List<MetricAggregation> result,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        ChronoUnit unit,
        String format
    ) {
        List<MetricAggregation> filledResult = new ArrayList<>();
        ZonedDateTime currentDate = startDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault());
        while (currentDate.isBefore(endDate)) {
            String finalCurrentDate = currentDate.format(formatter);
            MetricAggregation metricStat = result.stream()
                .filter(metric -> formatter.format(metric.date).equals(finalCurrentDate))
                .findFirst()
                .orElse(MetricAggregation.builder().date(currentDate.toInstant()).value(0.0).build());

            filledResult.add(metricStat);
            currentDate = currentDate.plus(1, unit);
        }

        return filledResult;
    }

    @Override
    public Function<String, String> sortMapping() throws IllegalArgumentException {
        Map<String, String> mapper = Map.of(
            "namespace", "namespace",
            "flowId", "flow_id",
            "taskId", "task_id",
            "executionId", "execution_id",
            "taskrunId", "taskrun_id",
            "name", "metric_name",
            "timestamp", "timestamp",
            "value", "metric_value"
        );

        return mapper::get;
    }
}
