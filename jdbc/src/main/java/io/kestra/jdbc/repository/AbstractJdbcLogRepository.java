package io.kestra.jdbc.repository;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Logs;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import lombok.Getter;
import reactor.core.publisher.Flux;

public abstract class AbstractJdbcLogRepository extends AbstractJdbcCrudRepository<LogEntry> implements LogRepositoryInterface {

    private static final Condition NORMAL_KIND_CONDITION = field("execution_kind").isNull().or(field("execution_kind").eq(ExecutionKind.NORMAL.name()));
    private static final String DATE_COLUMN = "timestamp";

    public AbstractJdbcLogRepository(io.kestra.jdbc.AbstractJdbcRepository<LogEntry> jdbcRepository, JdbcFilterService filterService) {
        super(jdbcRepository);

        this.filterService = filterService;
    }

    abstract protected Condition findCondition(String query);

    protected Condition findQueryCondition(String query) {
        return findCondition(query);
    }

    @Getter
    protected final JdbcFilterService filterService;

    protected Map<Logs.Fields, String> getFieldsMapping() {
        return Map.of(
            Logs.Fields.DATE, DATE_COLUMN, Logs.Fields.NAMESPACE, "namespace", Logs.Fields.FLOW_ID, "flow_id", Logs.Fields.TASK_ID, "task_id", Logs.Fields.EXECUTION_ID, "execution_id",
            Logs.Fields.TASK_RUN_ID, "taskrun_id",
            Logs.Fields.ATTEMPT_NUMBER, "attempt_number", Logs.Fields.TRIGGER_ID, "trigger_id", Logs.Fields.LEVEL, "level", Logs.Fields.MESSAGE, "message"
        );
    }

    protected Map<Logs.Fields, String> getWhereMapping() {
        return getFieldsMapping();
    }

    @Override
    public Set<Logs.Fields> dateFields() {
        return Set.of(Logs.Fields.DATE);
    }

    @Override
    public Logs.Fields dateFilterField() {
        return Logs.Fields.DATE;
    }

    @Override
    public ArrayListTotal<LogEntry> find(Pageable pageable, @Nullable String tenantId, @Nullable List<QueryFilter> filters) {
        var condition = NORMAL_KIND_CONDITION.and(this.filter(filters, DATE_COLUMN, Resource.LOG));
        return findPage(pageable, tenantId, condition);
    }

    /**
     * The log table column for {@code TASK_RUN_ID} is {@code taskrun_id} (one word), not
     * {@code task_run_id} as the default snake-case derivation would produce. Override the
     * default mapping for that one mismatch; the rest of the column names line up with
     * {@link QueryFilter.Field#name()} lower-cased.
     */
    @Override
    protected Name getColumnName(QueryFilter.Field field) {
        if (field == QueryFilter.Field.TASK_RUN_ID) {
            return DSL.quotedName("taskrun_id");
        }
        return super.getColumnName(field);
    }

    @Override
    public Flux<LogEntry> findAsync(@Nullable String tenantId, List<QueryFilter> filters) {
        var condition = NORMAL_KIND_CONDITION.and(this.filter(filters, DATE_COLUMN, Resource.LOG));
        return findAsync(tenantId, condition, field(DATE_COLUMN).asc());
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel) {
        return findByExecutionId(tenantId, executionId, minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdWithoutAcl(String tenantId, String executionId, Level minLevel) {
        return findByExecutionId(tenantId, executionId, minLevel, false);
    }

    private List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, boolean withAccessControl) {
        return this.query(tenantId, field("execution_id").eq(executionId), minLevel, withAccessControl);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, Pageable pageable) {
        return this.query(tenantId, field("execution_id").eq(executionId), minLevel, pageable);
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String namespace, String flowId, String executionId, Level minLevel) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("namespace").eq(namespace)).and(field("flow_id").eq(flowId)), minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel) {
        return findByExecutionIdAndTaskId(tenantId, executionId, taskId, minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskIdWithoutAcl(String tenantId, String executionId, String taskId, Level minLevel) {
        return findByExecutionIdAndTaskId(tenantId, executionId, taskId, minLevel, false);
    }

    private List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, boolean withAccessControl) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("task_id").eq(taskId)), minLevel, withAccessControl);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("task_id").eq(taskId)), minLevel, pageable);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String namespace, String flowId, String executionId, String taskId, Level minLevel) {
        return this
            .query(tenantId, field("execution_id").eq(executionId).and(field("namespace").eq(namespace)).and(field("flow_id").eq(flowId)).and(field("task_id").eq(taskId)), minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel) {
        return findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel) {
        return findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel, false);
    }

    private List<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, boolean withAccessControl) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("taskrun_id").eq(taskRunId)), minLevel, withAccessControl);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("taskrun_id").eq(taskRunId)), minLevel, pageable);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt) {
        return findByExecutionIdAndTaskRunIdAndAttempt(tenantId, executionId, taskRunId, minLevel, attempt, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt) {
        return findByExecutionIdAndTaskRunIdAndAttempt(tenantId, executionId, taskRunId, minLevel, attempt, false);
    }

    private List<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, boolean withAccessControl) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("taskrun_id").eq(taskRunId)).and(field("attempt_number").eq(attempt)), minLevel, withAccessControl);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable) {
        return this.query(tenantId, field("execution_id").eq(executionId).and(field("taskrun_id").eq(taskRunId)).and(field("attempt_number").eq(attempt)), minLevel, pageable);
    }

    @Override
    public Integer purge(Execution execution) {
        return purge(DSL.noCondition(), field("execution_id", String.class).eq(execution.getId()));
    }

    @Override
    public Integer purge(List<Execution> executions) {
        return purge(DSL.noCondition(), field("execution_id", String.class).in(executions.stream().map(Execution::getId).toList()));
    }

    @Override
    public void deleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt) {
        this.jdbcRepository.getDslContextWrapper().transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            var delete = context.delete(this.jdbcRepository.getTable()).where(this.defaultFilter(tenantId)).and(field("execution_id").eq(executionId));

            if (taskId != null) {
                delete = delete.and(field("task_id").eq(taskId));
            }

            if (taskRunId != null) {
                delete = delete.and(field("taskrun_id").eq(taskRunId));
            }

            if (minLevel != null) {
                delete = delete.and(minLevel(minLevel));
            }

            if (attempt != null) {
                delete = delete.and(field("attempt_number").eq(attempt));
            }

            delete.execute();
        });
    }

    @Override
    public void deleteByQuery(String tenantId, String namespace, String flowId, String triggerId) {
        this.jdbcRepository.getDslContextWrapper().transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            var delete = context.delete(this.jdbcRepository.getTable()).where(this.defaultFilter(tenantId)).and(field("namespace").eq(namespace)).and(field("flow_id").eq(flowId));

            if (triggerId != null) {
                delete = delete.and(field("trigger_id").eq(triggerId));
            }

            delete.execute();
        });
    }

    @Override
    public int deleteByQuery(String tenantId, String namespace, String flowId, String executionId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean purgeExecutionLogs, boolean purgeNonExecutionLogs, Integer batchSize) {
        Condition condition = buildDeleteCondition(tenantId, namespace, flowId, executionId, logLevels, startDate, endDate, purgeExecutionLogs, purgeNonExecutionLogs);

        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            if (batchSize != null && configuration.dialect().family() == SQLDialect.MYSQL) {
                int total = 0;
                int deleted;
                do {
                    deleted = DSL.using(configuration)
                        .delete(this.jdbcRepository.getTable())
                        .where(condition)
                        .limit(batchSize)
                        .execute();
                    total += deleted;
                } while (deleted > 0);
                return total;
            }
            return DSL.using(configuration).delete(this.jdbcRepository.getTable()).where(condition).execute();
        });
    }

    private Condition buildDeleteCondition(String tenantId, String namespace, String flowId, String executionId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean purgeExecutionLogs, boolean purgeNonExecutionLogs) {
        Condition condition = this.defaultFilter(tenantId)
            .and(field(DATE_COLUMN).lessOrEqual(endDate.toOffsetDateTime()));

        if (startDate != null) {
            condition = condition.and(field(DATE_COLUMN).greaterOrEqual(startDate.toOffsetDateTime()));
        }
        if (namespace != null) {
            condition = condition.and(field("namespace").eq(namespace));
        }
        if (flowId != null) {
            condition = condition.and(field("flow_id").eq(flowId));
        }
        if (executionId != null) {
            condition = condition.and(field("execution_id").eq(executionId));
        }
        if (logLevels != null) {
            condition = condition.and(levelsCondition(logLevels));
        }
        if (purgeExecutionLogs && !purgeNonExecutionLogs) {
            condition = condition.and(field("execution_id").isNotNull());
        } else if (purgeNonExecutionLogs && !purgeExecutionLogs) {
            condition = condition.and(field("execution_id").isNull());
        }

        return condition;
    }

    @Override
    public void deleteByFilters(String tenantId, List<QueryFilter> filters) {
        this.jdbcRepository.getDslContextWrapper().transactionResult(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            var delete = context.delete(this.jdbcRepository.getTable()).where(this.defaultFilter(tenantId));
            delete = delete.and(this.filter(filters, DATE_COLUMN, Resource.LOG));

            return delete.execute();
        });
    }

    private ArrayListTotal<LogEntry> query(String tenantId, Condition condition, Level minLevel, Pageable pageable) {
        var theCondition = minLevel != null ? condition.and(minLevel(minLevel)) : condition;
        return findPage(pageable, tenantId, theCondition);
    }

    private List<LogEntry> query(String tenantId, Condition condition, Level minLevel, boolean withAccessControl) {
        var defaultFilter = withAccessControl ? this.defaultFilter(tenantId) : this.defaultFilterWithNoACL(tenantId);
        var theCondition = minLevel != null ? condition.and(minLevel(minLevel)) : condition;
        return find(defaultFilter, theCondition, field(DATE_COLUMN).sort(SortOrder.ASC));
    }

    private Condition minLevel(Level minLevel) {
        return levelsCondition(LogEntry.findLevelsByMin(minLevel));
    }

    protected Condition levelsCondition(List<Level> levels) {
        return field("level").in(levels.stream().map(level -> level.name()).toList());
    }

    public Double fetchValue(String tenantId, DataFilterKPI<Logs.Fields, ? extends ColumnDescriptor<Logs.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration ->
        {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<Logs.Fields> columnDescriptor = dataFilter.getColumns();
            Field<?> field = columnToField(columnDescriptor, getFieldsMapping());
            if (columnDescriptor.getAgg() != null) {
                field = filterService.buildAggregation(field, columnDescriptor.getAgg());
            }

            List<AbstractFilter<Logs.Fields>> filters = new ArrayList<>(ListUtils.emptyOnNull(dataFilter.getWhere()));
            if (numeratorFilter) {
                filters.addAll(dataFilter.getNumerator());
            }

            SelectConditionStep selectStep = context.select(field).from(this.jdbcRepository.getTable()).where(this.defaultFilter(tenantId));

            var selectConditionStep = where(selectStep, filterService, filters, getFieldsMapping()).and(NORMAL_KIND_CONDITION);

            Record result = selectConditionStep.fetchOne();
            if (result != null) {
                return result.getValue(field, Double.class);
            } else {
                return null;
            }
        });
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(String tenantId, DataFilter<Logs.Fields, ? extends ColumnDescriptor<Logs.Fields>> descriptors, ZonedDateTime startDate,
        ZonedDateTime endDate, Pageable pageable) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            Map<String, ? extends ColumnDescriptor<Logs.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
                .filter(entry -> entry.getValue().getField() == null || !dateFields().contains(entry.getValue().getField()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            boolean hasAgg = descriptors.getColumns().entrySet().stream().anyMatch(col -> col.getValue().getAgg() != null);
            // Generate custom fields for date as they probably need formatting
            // If they don't have aggs, we format datetime to minutes
            List<Field<Date>> dateFields = generateDateFields(descriptors, getFieldsMapping(), startDate, endDate, dateFields(), hasAgg ? null : DateUtils.GroupType.MINUTE);

            // Init request
            SelectConditionStep<Record> selectConditionStep = select(context, filterService, columnsWithoutDate, dateFields, this.getFieldsMapping(), this.jdbcRepository.getTable(), tenantId);

            // Apply Where filter
            selectConditionStep = where(selectConditionStep, filterService, descriptors.getWhere(), getWhereMapping()).and(NORMAL_KIND_CONDITION);

            List<? extends ColumnDescriptor<Logs.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream().filter(column -> column.getAgg() == null).toList();

            // Apply GroupBy for aggregation
            SelectHavingStep<Record> selectHavingStep = groupBy(selectConditionStep, columnsWithoutDateWithOutAggs, dateFields, getFieldsMapping());

            // Apply OrderBy
            SelectSeekStepN<Record> selectSeekStep = orderBy(selectHavingStep, descriptors);

            // Fetch and paginate if provided
            return fetchSeekStep(selectSeekStep, pageable);
        });
    }

    @Override
    protected Condition defaultFilterWithNoACL(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return defaultFilterWithNoACL(tenantId);
    }

    @Override
    protected Condition defaultFilter(Boolean allowDeleted) {
        return this.defaultFilter();
    }

    @Override
    protected Condition defaultFilterWithNoACL(String tenantId, boolean deleted) {
        return this.defaultFilterWithNoACL(tenantId);
    }

    @Override
    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        return this.defaultFilter(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);
}
