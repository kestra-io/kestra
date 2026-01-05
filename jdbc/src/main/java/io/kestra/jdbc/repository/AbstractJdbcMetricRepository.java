package io.kestra.jdbc.repository;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregation;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Metrics;
import io.micrometer.common.lang.Nullable;
import io.micronaut.data.model.Pageable;
import lombok.Getter;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractJdbcMetricRepository extends AbstractJdbcCrudRepository<MetricEntry> implements MetricRepositoryInterface {
    private static final Condition NORMAL_KIND_CONDITION = field("execution_kind").isNull().or(field("execution_kind").eq(ExecutionKind.NORMAL.name()));

    public AbstractJdbcMetricRepository(io.kestra.jdbc.AbstractJdbcRepository<MetricEntry> jdbcRepository,
                                        QueueService queueService,
                                        JdbcFilterService filterService) {
        super(jdbcRepository, queueService);

        this.filterService = filterService;
    }

    @Getter
    private final JdbcFilterService filterService;

    @Getter
    private final Map<Metrics.Fields, String> fieldsMapping = Map.of(
        Metrics.Fields.NAMESPACE, "namespace",
        Metrics.Fields.FLOW_ID, "flow_id",
        Metrics.Fields.TASK_ID, "task_id",
        Metrics.Fields.EXECUTION_ID, "execution_d",
        Metrics.Fields.TASK_RUN_ID, "taskrun_id",
        Metrics.Fields.NAME, "metric_name",
        Metrics.Fields.VALUE, "metric_value",
        Metrics.Fields.DATE, "timestamp"
    );

    @Override
    public Set<Metrics.Fields> dateFields() {
        return Set.of(Metrics.Fields.DATE);
    }

    @Override
    public Metrics.Fields dateFilterField() {
        return Metrics.Fields.DATE;
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionId(String tenantId, String executionId, Pageable pageable) {
        return this.findPage(
            pageable,
            tenantId,
            field("execution_id").eq(executionId)
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Pageable pageable) {
        return this.findPage(
            pageable,
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId))
        );
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Pageable pageable) {
        return this.findPage(
            pageable,
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
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
                .and(field("namespace").eq(namespace))
                .and(NORMAL_KIND_CONDITION),
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
                .and(field("task_id").eq(taskId))
                .and(NORMAL_KIND_CONDITION),
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
                .and(field("namespace").eq(namespace))
                .and(NORMAL_KIND_CONDITION),
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
            .and(field("metric_name").eq(metric))
            .and(NORMAL_KIND_CONDITION);
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

    @Override
    public Integer purge(List<Execution> executions) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                return context.delete(this.jdbcRepository.getTable())
                    // The deleted field is not used, so ti will always be false.
                    // We add it here to be sure to use the correct index.
                    .where(field("deleted", Boolean.class).eq(false))
                    .and(field("execution_id", String.class).in(executions.stream().map(Execution::getId).toList()))
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

    public Double fetchValue(String tenantId, DataFilterKPI<Metrics.Fields, ? extends ColumnDescriptor<Metrics.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate, boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<Metrics.Fields> columnDescriptor = dataFilter.getColumns();
            Field<?> field = columnToField(columnDescriptor, getFieldsMapping());
            if (columnDescriptor.getAgg() != null) {
                field = filterService.buildAggregation(field, columnDescriptor.getAgg());
            }

            List<AbstractFilter<Metrics.Fields>> filters = new ArrayList<>(ListUtils.emptyOnNull(dataFilter.getWhere()));
            if (numeratorFilter) {
                filters.addAll(dataFilter.getNumerator());
            }

            SelectConditionStep selectStep = context
                .select(field)
                .from(this.jdbcRepository.getTable())
                .where(this.defaultFilter(tenantId));

            var selectConditionStep = where(
                selectStep,
                filterService,
                filters,
                getFieldsMapping()
            );

            Record result = selectConditionStep.fetchOne();
            if (result != null) {
                return result.getValue(field, Double.class);
            } else {
                return null;
            }
        });
    }


    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Metrics.Fields, ? extends ColumnDescriptor<Metrics.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<String, ? extends ColumnDescriptor<Metrics.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
                    .filter(entry -> entry.getValue().getField() == null || !dateFields().contains(entry.getValue().getField()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                boolean hasAgg = descriptors.getColumns().entrySet().stream().anyMatch(col -> col.getValue().getAgg() != null);
                // Generate custom fields for date as they probably need formatting
                // If they don't have aggs, we format datetime to minutes
                List<Field<Date>> dateFields = generateDateFields(descriptors, fieldsMapping, startDate, endDate, dateFields(), hasAgg ? null : DateUtils.GroupType.MINUTE);

                // Init request
                SelectConditionStep<Record> selectConditionStep = select(
                    context,
                    filterService,
                    columnsWithoutDate,
                    dateFields,
                    this.getFieldsMapping(),
                    this.jdbcRepository.getTable(),
                    tenantId
                );

                // Apply Where filter
                selectConditionStep = where(selectConditionStep, filterService, descriptors.getWhere(), fieldsMapping);

                List<? extends ColumnDescriptor<Metrics.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream()
                    .filter(column -> column.getAgg() == null)
                    .toList();

                // Apply GroupBy for aggregation
                SelectHavingStep<Record> selectHavingStep = groupBy(
                    selectConditionStep,
                    columnsWithoutDateWithOutAggs,
                    dateFields,
                    fieldsMapping
                );

                // Apply OrderBy
                SelectSeekStepN<Record> selectSeekStep = orderBy(selectHavingStep, descriptors);

                // Fetch and paginate if provided
                return fetchSeekStep(selectSeekStep, pageable);
            });
    }

    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);
}
