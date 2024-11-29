package io.kestra.jdbc.repository;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregation;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.dashboard.data.Metrics;
import io.micrometer.common.lang.Nullable;
import io.micronaut.data.model.Pageable;
import org.apache.commons.lang3.NotImplementedException;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractJdbcMetricRepository extends AbstractJdbcRepository implements MetricRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository;

    public AbstractJdbcMetricRepository(io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionId(String tenantId, String executionId, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
            , pageable
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            pageable
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            pageable
        );
    }

    @Override
    public List<String> flowMetrics(
        String tenantId,
        String namespace,
        String flowId
    ) {
        return this.queryDistinct(
            tenantId,
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace)),
            "metric_name"
        );
    }

    @Override
    public List<String> taskMetrics(
        String tenantId,
        String namespace,
        String flowId,
        String taskId
    ) {
        return this.queryDistinct(
            tenantId,
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace))
                .and(field("task_id").eq(taskId)),
            "metric_name"
        );
    }

    @Override
    public List<String> tasksWithMetrics(
        String tenantId,
        String namespace,
        String flowId
    ) {
        return this.queryDistinct(
            tenantId,
            field("flow_id").eq(flowId)
                .and(field("namespace").eq(namespace)),
            "task_id"
        );
    }

    @Override
    public MetricAggregations aggregateByFlowId(
        String tenantId,
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
                    tenantId,
                    conditions,
                    startDate,
                    endDate,
                    aggregation
                ))
            .groupBy(DateUtils.groupByType(Duration.between(startDate, endDate)).val())
            .build();
    }

    @Override
    public MetricEntry save(MetricEntry metric) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(metric);
        this.jdbcRepository.persist(metric, fields);

        return metric;
    }

    @Override
    public int saveBatch(List<MetricEntry> items) {
        if (ListUtils.isEmpty(items)) {
            return 0;
        }

        return this.jdbcRepository.persistBatch(items);
    }

    @Override
    public Integer purge(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                return context.delete(this.jdbcRepository.getTable())
                    // The deleted field is not used, so ti will always be false.
                    // We add it here to be sure to use the correct index.
                    .where(field("deleted", Boolean.class).eq(false))
                    .and(field("execution_id", String.class).eq(execution.getId()))
                    .execute();
            });
    }

    private List<String> queryDistinct(String tenantId, Condition condition, String field) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record1<Object>> select = context
                    .selectDistinct(field(field))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                return select.fetch().map(record -> record.get(field, String.class));
            });
    }

    private ArrayListTotal<MetricEntry> query(String tenantId, Condition condition, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    private List<MetricAggregation> aggregate(
        String tenantId,
        Condition condition,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        String aggregation
    ) {
        List<Field<?>> dateFields = new ArrayList<>(groupByFields(Duration.between(startDate, endDate), true));
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
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                if (startDate != null) {
                    select = select.and(field("timestamp").greaterOrEqual(startDate.toOffsetDateTime()));
                }

                if (endDate != null) {
                    select = select.and(field("timestamp").lessOrEqual(endDate.toOffsetDateTime()));
                }

                dateFields.add(field("metric_name"));

                List<Field<?>> groupByFields = new ArrayList<>(groupByFields(Duration.between(startDate, endDate)));
                groupByFields.add(field("metric_name"));
                var selectGroup = select.groupBy(groupByFields);

                List<MetricAggregation> result = this.jdbcRepository
                    .fetchMetricStat(selectGroup, DateUtils.groupByType(Duration.between(startDate, endDate)).val());

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

    private List<MetricAggregation> fillDate(List<MetricAggregation> result, ZonedDateTime startDate, ZonedDateTime endDate) {
        DateUtils.GroupType groupByType = DateUtils.groupByType(Duration.between(startDate, endDate));

        if (groupByType.equals(DateUtils.GroupType.MONTH)) {
            return fillDate(result, startDate, endDate, ChronoUnit.MONTHS, "YYYY-MM");
        } else if (groupByType.equals(DateUtils.GroupType.WEEK)) {
            return fillDate(result, startDate, endDate, ChronoUnit.WEEKS, "YYYY-ww");
        } else if (groupByType.equals(DateUtils.GroupType.DAY)) {
            return fillDate(result, startDate, endDate, ChronoUnit.DAYS, "YYYY-MM-DD");
        } else if (groupByType.equals(DateUtils.GroupType.HOUR)) {
            return fillDate(result, startDate, endDate, ChronoUnit.HOURS, "YYYY-MM-DD HH");
        } else {
            return fillDate(result, startDate, endDate, ChronoUnit.MINUTES, "YYYY-MM-DD HH:mm");
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

    @Override
    public List<Map<String, Object>> fetchData(String tenantId, DataFilter<Metrics.Fields, ? extends ColumnDescriptor<Metrics.Fields>> filter, ZonedDateTime startDate, ZonedDateTime endDate) throws IOException {
        throw new NotImplementedException();
    }
}
