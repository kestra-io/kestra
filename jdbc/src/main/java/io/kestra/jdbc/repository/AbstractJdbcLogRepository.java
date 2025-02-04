package io.kestra.jdbc.repository;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Logs;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import lombok.Getter;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractJdbcLogRepository extends AbstractJdbcRepository implements LogRepositoryInterface {

    protected static final int FETCH_SIZE = 100;

    protected io.kestra.jdbc.AbstractJdbcRepository<LogEntry> jdbcRepository;

    public AbstractJdbcLogRepository(io.kestra.jdbc.AbstractJdbcRepository<LogEntry> jdbcRepository,
                                     JdbcFilterService filterService) {
        this.jdbcRepository = jdbcRepository;

        this.filterService = filterService;
    }

    abstract protected Condition findCondition(String query);

    @Getter
    private final JdbcFilterService filterService;

    @Getter
    private final Map<Logs.Fields, String> fieldsMapping = Map.of(
        Logs.Fields.DATE, "timestamp",
        Logs.Fields.NAMESPACE, "namespace",
        Logs.Fields.FLOW_ID, "flow_id",
        Logs.Fields.TASK_ID, "task_id",
        Logs.Fields.EXECUTION_ID, "execution_id",
        Logs.Fields.TASK_RUN_ID, "taskrun_id",
        Logs.Fields.ATTEMPT_NUMBER, "attempt_number",
        Logs.Fields.TRIGGER_ID, "trigger_id",
        Logs.Fields.LEVEL, "level",
        Logs.Fields.MESSAGE, "message"
    );

    @Override
    public Set<Logs.Fields> dateFields() {
        return Set.of(Logs.Fields.DATE);
    }

    @Override
    public Logs.Fields dateFilterField() {
        return Logs.Fields.DATE;
    }

    @Override
    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable String triggerId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                this.filter(select, query, namespace, flowId, triggerId, minLevel, startDate , endDate);

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    private <T extends Record> SelectConditionStep<T> filter(
        SelectConditionStep<T> select,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable String triggerId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        select = addNamespace(select, namespace);

        if (flowId != null) {
            select = select.and(field("flow_id").eq(flowId));
        }

        if (triggerId != null) {
            select = select.and(field("trigger_id").eq(triggerId));
        }

        select = addMinLevel(select, minLevel);

        if (query != null) {
            select = select.and(this.findCondition(query));
        }

        if (startDate != null) {
            select = select.and(field("timestamp").greaterOrEqual(startDate.toOffsetDateTime()));
        }

        if (endDate != null) {
            select = select.and(field("timestamp").lessOrEqual(endDate.toOffsetDateTime()));
        }

        return select;
    }

    private <T extends Record> SelectConditionStep<T> addMinLevel(SelectConditionStep<T> select,
        Level minLevel) {
        if (minLevel != null) {
            select = select.and(minLevel(minLevel));
        }
        return select;
    }

    private static <T extends Record> SelectConditionStep<T> addNamespace(SelectConditionStep<T> select,
        String namespace) {
        if (namespace != null) {
            select = select.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
        }
        return select;
    }

    @Override
    public Flux<LogEntry> findAsync(
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable Level minLevel,
        ZonedDateTime startDate
    ){
        return Flux.create(emitter -> this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));
                addNamespace(select, namespace);
                addMinLevel(select, minLevel);
                select = select.and(field("timestamp").greaterThan(startDate.toOffsetDateTime()));

                Select<Record1<Object>> query = this.jdbcRepository.buildPageQuery(context, select);

                try (Stream<Record1<Object>> stream = query.fetchSize(FETCH_SIZE).stream()){
                    stream.map((Record record) -> jdbcRepository.map(record))
                        .forEach(emitter::next);
                } finally {
                    emitter.complete();
                }
            }), FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public List<LogStatistics> statistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy
    ) {
        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;
        DateUtils.GroupType groupByType = DateUtils.groupByType(Duration.between(finalStartDate, finalEndDate));

        List<Field<String>> fields = List.of(field("level", String.class));

        List<Field<?>> dateFields = new ArrayList<>(groupByFields(Duration.between(finalStartDate, finalEndDate), "timestamp", groupBy));
        List<Field<?>> selectFields = new ArrayList<>(fields);
        selectFields.add(
            DSL.count().as("count")
        );
        selectFields.addAll(groupByFields(Duration.between(finalStartDate, finalEndDate), "timestamp", groupBy, true));

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record> select = context
                    .select(selectFields)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                this.filter(select, query, namespace, flowId, null, minLevel, startDate, endDate);

                List<Field<?>> groupFields = new ArrayList<>(fields);
                groupFields.addAll(dateFields);

                SelectHavingStep<?> finalQuery = select
                    .groupBy(groupFields);

                List<LogStatistics> result = finalQuery
                    .fetch()
                    .map(record -> {
                        Instant date = this.jdbcRepository.getDate(record, groupByType.val());
                        LogStatistics base = LogStatistics
                            .builder()
                            .timestamp(date)
                            .groupBy(groupByType.val())
                            .build();

                        HashMap<Level, Long> counts = new HashMap<>(base.getCounts());
                        counts.put(
                            record.get("level", Level.class),
                            record.get("count", Long.class)
                        );

                        return base
                            .toBuilder()
                            .counts(counts)
                            .build();
                    });

                return fillDate(result, finalStartDate, finalEndDate);
            })
            .stream()
            .sorted(Comparator.comparing(LogStatistics::getTimestamp))
            .toList();
    }

    private List<LogStatistics> fillDate(List<LogStatistics> result, ZonedDateTime startDate, ZonedDateTime endDate) {
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

    private List<LogStatistics> fillDate(
        List<LogStatistics> result,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        ChronoUnit unit,
        String format
    ) {
        DateUtils.GroupType groupByType = DateUtils.groupByType(Duration.between(startDate, endDate));
        List<LogStatistics> filledResult = new ArrayList<>();
        ZonedDateTime currentDate = startDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault());
        while (currentDate.isBefore(endDate)) {
            String finalCurrentDate = currentDate.format(formatter);

            LogStatistics current = result.stream()
                .filter(metric -> formatter.format(metric.getTimestamp()).equals(finalCurrentDate))
                .collect(Collectors.groupingBy(LogStatistics::getTimestamp))
                .values()
                .stream()
                .map(logStatistics -> {
                    Map<Level, Long> collect = logStatistics
                        .stream()
                        .map(LogStatistics::getCounts)
                        .flatMap(levelLongMap -> levelLongMap.entrySet().stream())
                        .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingLong(Map.Entry::getValue)));

                    return logStatistics.getFirst().toBuilder().counts(collect).build();
                })
                .findFirst()
                .orElse(LogStatistics.builder().timestamp(currentDate.toInstant()).groupBy(groupByType.val()).build());

            filledResult.add(current);
            currentDate = currentDate.plus(1, unit);
        }

        return filledResult;
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel) {
        return findByExecutionId(tenantId,  executionId, minLevel, true);
    }

    @Override
    public List<LogEntry> findByExecutionIdWithoutAcl(String tenantId, String executionId, Level minLevel) {
        return findByExecutionId(tenantId,  executionId, minLevel, false);
    }

    private List<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, boolean withAccessControl) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId),
            minLevel,
            withAccessControl
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String tenantId, String executionId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId),
            minLevel,
            pageable
        );
    }

    @Override
    public List<LogEntry> findByExecutionId(String tenantId, String namespace, String flowId, String executionId, Level minLevel) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("namespace").eq(namespace))
                .and(field("flow_id").eq(flowId)),
            minLevel,
            true
        );
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
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            minLevel,
            withAccessControl
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("task_id").eq(taskId)),
            minLevel,
            pageable
        );
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String tenantId, String namespace, String flowId, String executionId, String taskId, Level minLevel) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("namespace").eq(namespace))
                .and(field("flow_id").eq(flowId))
                .and(field("task_id").eq(taskId)),
            minLevel,
            true
        );
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
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            minLevel,
            withAccessControl
        );
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Level minLevel, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId)),
            minLevel,
            pageable
        );
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
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
                .and(field("attempt_number").eq(attempt)),
            minLevel,
            withAccessControl
        );
    }


    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunIdAndAttempt(String tenantId, String executionId, String taskRunId, Level minLevel, Integer attempt, Pageable pageable) {
        return this.query(
            tenantId,
            field("execution_id").eq(executionId)
                .and(field("taskrun_id").eq(taskRunId))
                .and(field("attempt_number").eq(attempt)),
            minLevel,
            pageable
        );
    }

    @Override
    public LogEntry save(LogEntry log) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(log);
        this.jdbcRepository.persist(log, fields);

        return log;
    }

    @Override
    public int saveBatch(List<LogEntry> items) {
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

    @Override
    public void deleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                var delete = context
                    .delete(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("execution_id").eq(executionId));

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
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                var delete = context
                    .delete(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId));

                if (triggerId != null) {
                    delete = delete.and(field("trigger_id").eq(triggerId));
                }

                delete.execute();
            });
    }

    @Override
    public int deleteByQuery(String tenantId, String namespace, String flowId, List<Level> logLevels, ZonedDateTime startDate, ZonedDateTime endDate) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                var delete = context
                    .delete(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("timestamp").lessOrEqual(endDate.toOffsetDateTime()));

                if (startDate != null) {
                    delete = delete.and(field("timestamp").greaterOrEqual(startDate.toOffsetDateTime()));
                }

                if (namespace != null) {
                    delete = delete.and(field("namespace").eq(namespace));
                }

                if (flowId != null) {
                    delete = delete.and(field("flow_id").eq(flowId));
                }

                if (logLevels != null) {
                    delete = delete.and(levelsCondition(logLevels));
                }

                return delete.execute();
            });
    }

    private ArrayListTotal<LogEntry> query(String tenantId, Condition condition, Level minLevel, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(condition);

                if (minLevel != null) {
                    select = select.and(minLevel(minLevel));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable
                );
            });
    }

    private List<LogEntry> query(String tenantId, Condition condition, Level minLevel, boolean withAccessControl) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(withAccessControl ? this.defaultFilter(tenantId) : this.defaultFilterWithNoACL(tenantId));

                select = select.and(condition);

                if (minLevel != null) {
                    select = select.and(minLevel(minLevel));
                }

                return this.jdbcRepository.fetch(select
                    .orderBy(field("timestamp").sort(SortOrder.ASC))
                );
            });
    }

    private Condition minLevel(Level minLevel) {
        return levelsCondition(LogEntry.findLevelsByMin(minLevel));
    }

    protected Condition levelsCondition(List<Level> levels) {
        return field("level").in(levels.stream().map(level -> level.name()).toList());
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Logs.Fields, ? extends ColumnDescriptor<Logs.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<String, ? extends ColumnDescriptor<Logs.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
                    .filter(entry -> entry.getValue().getField() == null || !dateFields().contains(entry.getValue().getField()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                // Generate custom fields for date as they probably need formatting
                List<Field<Date>> dateFields = generateDateFields(descriptors, fieldsMapping, startDate, endDate, dateFields());

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
                selectConditionStep = where(selectConditionStep, filterService, descriptors, fieldsMapping);

                List<? extends ColumnDescriptor<Logs.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream()
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
                List<Map<String, Object>> results = fetchSeekStep(selectSeekStep, pageable);

                // Fetch total count for pagination
                int total = context.fetchCount(selectConditionStep);

                return new ArrayListTotal<>(results, total);
            });
    }

    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);

    protected <F extends Enum<F>> List<Field<Date>> generateDateFields(
        DataFilter<F, ? extends ColumnDescriptor<F>> descriptors,
        Map<F, String> fieldsMapping,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Set<F> dateFields
    ) {
        return descriptors.getColumns().entrySet().stream()
            .filter(entry -> entry.getValue().getAgg() == null && dateFields.contains(entry.getValue().getField()))
            .map(entry -> {
                Duration duration = Duration.between(startDate, endDate);
                return formatDateField(fieldsMapping.get(entry.getValue().getField()), DateUtils.groupByType(duration)).as(entry.getKey());
            })
            .toList();

    }
}
