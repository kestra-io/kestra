package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.Contains;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.*;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorState;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.runner.JdbcQueueIndexerInterface;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractJdbcExecutionRepository extends AbstractJdbcRepository implements ExecutionRepositoryInterface, JdbcQueueIndexerInterface<Execution> {
    private static final int FETCH_SIZE = 100;
    private static final Field<String> STATE_CURRENT_FIELD = field("state_current", String.class);
    private static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);
    private static final Field<Object> START_DATE_FIELD = field("start_date");

    protected final io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;
    private final ApplicationContext applicationContext;
    protected final AbstractJdbcExecutorStateStorage executorStateStorage;

    private QueueInterface<Execution> executionQueue;
    private NamespaceUtils namespaceUtils;

    private final JdbcFilterService filterService;

    @Getter
    private final Map<Executions.Fields, String> fieldsMapping = Map.of(
        Executions.Fields.ID, "key",
        Executions.Fields.NAMESPACE, "namespace",
        Executions.Fields.FLOW_ID, "flow_id",
        Executions.Fields.STATE, "state_current",
        Executions.Fields.DURATION, "state_duration",
        Executions.Fields.LABELS, "labels",
        Executions.Fields.START_DATE, "start_date",
        Executions.Fields.END_DATE, "end_date",
        Executions.Fields.TRIGGER_EXECUTION_ID, "trigger_execution_id"
    );

    @Override
    public Set<Executions.Fields> dateFields() {
        return Set.of(Executions.Fields.START_DATE, Executions.Fields.END_DATE);
    }

    @Override
    public Executions.Fields dateFilterField() {
        return Executions.Fields.START_DATE;
    }

    @SuppressWarnings("unchecked")
    public AbstractJdbcExecutionRepository(
        io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository,
        ApplicationContext applicationContext,
        AbstractJdbcExecutorStateStorage executorStateStorage,
        JdbcFilterService filterService
    ) {
        this.jdbcRepository = jdbcRepository;
        this.executorStateStorage = executorStateStorage;
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.namespaceUtils = applicationContext.getBean(NamespaceUtils.class);

        // we inject ApplicationContext in order to get the ExecutionQueue lazy to avoid StackOverflowError
        this.applicationContext = applicationContext;

        this.filterService = filterService;
    }

    @SuppressWarnings("unchecked")
    private QueueInterface<Execution> executionQueue() {
        if (this.executionQueue == null) {
            this.executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        }

        return this.executionQueue;
    }

    public Boolean isTaskRunEnabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Flux<Execution> findAllByTriggerExecutionId(String tenantId,
                                                       String triggerExecutionId) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    SelectConditionStep<Record1<Object>> select = DSL
                        .using(configuration)
                        .select(field("value"))
                        .from(this.jdbcRepository.getTable())
                        .where(this.defaultFilter(tenantId))
                        .and(field("trigger_execution_id").eq(triggerExecutionId));

                    // fetchSize will fetch rows 100 by 100 even for databases where the driver loads all in memory
                    // using a stream will fetch lazily, otherwise all fetches would be done before starting emitting the items
                    try (var stream = select.fetchSize(FETCH_SIZE).stream()) {
                        stream.map(this.jdbcRepository::map).forEach(emitter::next);
                    } finally {
                        emitter.complete();
                    }
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<Execution> findLatestForStates(String tenantId, String namespace, String flowId, List<State.Type> states) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId, false))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId))
                    .and(statesFilter(states))
                    .orderBy(field("start_date").desc());
                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public Optional<Execution> findById(String tenantId, String id, boolean allowDeleted) {
        return findById(tenantId, id, allowDeleted, true);
    }

    @Override
    public Optional<Execution> findByIdWithoutAcl(String tenantId, String id) {
        return findById(tenantId, id, false, false);
    }

    public Optional<Execution> findById(String tenantId, String id, boolean allowDeleted, boolean withAccessControl) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(withAccessControl ? this.defaultFilter(tenantId, allowDeleted) : this.defaultFilterWithNoACL(tenantId, allowDeleted))
                    .and(field("key").eq(id));
                return this.jdbcRepository.fetchOne(from);
            });
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    protected Condition statesFilter(List<State.Type> state) {
        return field("state_current")
            .in(state.stream().map(Enum::name).toList());
    }

    @Override
    public ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.findSelect(
                    context,
                    query,
                    tenantId,
                    scope,
                    namespace,
                    flowId,
                    startDate,
                    endDate,
                    state,
                    labels,
                    triggerExecutionId,
                    childFilter,
                    false
                );

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public Flux<Execution> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter,
        boolean deleted
    ) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    DSLContext context = DSL.using(configuration);

                    SelectConditionStep<Record1<Object>> select = this.findSelect(
                        context,
                        query,
                        tenantId,
                        scope,
                        namespace,
                        flowId,
                        startDate,
                        endDate,
                        state,
                        labels,
                        triggerExecutionId,
                        childFilter,
                        deleted
                    );

                    // fetchSize will fetch rows 100 by 100 even for databases where the driver loads all in memory
                    // using a stream will fetch lazily, otherwise all fetches would be done before starting emitting the items
                    try (var stream = select.fetchSize(FETCH_SIZE).stream()) {
                        stream.map(this.jdbcRepository::map).forEach(emitter::next);
                    } finally {
                        emitter.complete();
                    }
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    private SelectConditionStep<Record1<Object>> findSelect(
        DSLContext context,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter,
        boolean deleted
    ) {
        SelectConditionStep<Record1<Object>> select = context
            .select(
                field("value")
            )
            .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter(tenantId, deleted));

        select = filteringQuery(select, scope, namespace, flowId, null, query, labels, triggerExecutionId, childFilter);

        if (startDate != null) {
            select = select.and(START_DATE_FIELD.greaterOrEqual(startDate.toOffsetDateTime()));
        }

        if (endDate != null) {
            select = select.and(field("end_date").lessOrEqual(endDate.toOffsetDateTime()));
        }

        if (state != null) {
            select = select.and(this.statesFilter(state));
        }

        return select;
    }

    @Override
    public ArrayListTotal<Execution> findByFlowId(String tenantId, String namespace, String id, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(id));

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> states,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatisticsForAllTenants(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        boolean isTaskRun
    ) {
        if (isTaskRun) {
            throw new UnsupportedOperationException();
        }

        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        Results results = dailyStatisticsQueryForAllTenants(
            List.of(
                STATE_CURRENT_FIELD
            ),
            query,
            namespace,
            flowId,
            null,
            finalStartDate,
            finalEndDate,
            groupBy,
            null
        );

        return dailyStatisticsQueryMapRecord(
            results.resultsOrRows()
                .getFirst()
                .result(),
            finalStartDate,
            finalEndDate,
            groupBy
        );
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        @Nullable List<State.Type> states,
        boolean isTaskRun
    ) {
        if (isTaskRun) {
            throw new UnsupportedOperationException();
        }

        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        Results results = dailyStatisticsQuery(
            List.of(
                STATE_CURRENT_FIELD
            ),
            query,
            tenantId,
            scope,
            namespace,
            flowId,
            null,
            finalStartDate,
            finalEndDate,
            groupBy,
            states
        );

        return dailyStatisticsQueryMapRecord(
            results.resultsOrRows()
                .getFirst()
                .result(),
            finalStartDate,
            finalEndDate,
            groupBy
        );
    }

    private List<DailyExecutionStatistics> dailyStatisticsQueryMapRecord(
        Result<Record> records,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupType
    ) {
        DateUtils.GroupType groupByType = groupType != null ? groupType : DateUtils.groupByType(Duration.between(startDate, endDate));

        return fillDate(records
            .stream()
            .map(record ->
                ExecutionStatistics.builder()
                    .date(this.jdbcRepository.getDate(record, groupByType.val()))
                    .durationMax(record.get("duration_max", Long.class))
                    .durationMin(record.get("duration_min", Long.class))
                    .durationSum(record.get("duration_sum", Long.class))
                    .stateCurrent(record.get("state_current", String.class))
                    .count(record.get("count", Long.class))
                    .build()
            )
            .collect(Collectors.groupingBy(ExecutionStatistics::getDate))
            .entrySet()
            .stream()
            .map(dateResultEntry -> dailyExecutionStatisticsMap(dateResultEntry.getKey(), dateResultEntry.getValue(), groupByType.val()))
            .sorted(Comparator.comparing(DailyExecutionStatistics::getStartDate))
            .toList(), startDate, endDate);
    }

    private Results dailyStatisticsQueryForAllTenants(
        List<Field<?>> fields,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        List<FlowFilter> flows,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        @Nullable List<State.Type> state
    ) {
        return dailyStatisticsQuery(
            this.defaultFilter(),
            fields,
            query,
            null,
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            groupBy,
            state
        );
    }

    private Results dailyStatisticsQuery(
        List<Field<?>> fields,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        List<FlowFilter> flows,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        @Nullable List<State.Type> state
    ) {
        return dailyStatisticsQuery(
            this.defaultFilter(tenantId),
            fields,
            query,
            scope,
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            groupBy,
            state
        );
    }

    private Results dailyStatisticsQuery(
        Condition defaultFilter,
        List<Field<?>> fields,
        @Nullable String query,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        List<FlowFilter> flows,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        @Nullable List<State.Type> state
    ) {
        List<Field<?>> dateFields = new ArrayList<>(groupByFields(Duration.between(startDate, endDate), "start_date", groupBy));
        List<Field<?>> selectFields = new ArrayList<>(fields);
        selectFields.addAll(List.of(
            DSL.count().as("count"),
            DSL.min(field("state_duration", Long.class)).as("duration_min"),
            DSL.max(field("state_duration", Long.class)).as("duration_max"),
            DSL.sum(field("state_duration", Long.class)).as("duration_sum")
        ));
        selectFields.addAll(groupByFields(Duration.between(startDate, endDate), "start_date", groupBy, true));

        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<?> select = context
                    .select(selectFields)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(START_DATE_FIELD.greaterOrEqual(startDate.toOffsetDateTime()))
                    .and(START_DATE_FIELD.lessOrEqual(endDate.toOffsetDateTime()));

                select = filteringQuery(select, scope, namespace, flowId, flows, query, null, null, null);

                if (state != null) {
                    select = select.and(this.statesFilter(state));
                }

                List<Field<?>> groupFields = new ArrayList<>(fields);

                groupFields.addAll(dateFields);

                SelectHavingStep<?> finalQuery = select
                    .groupBy(groupFields);

                return finalQuery.fetchMany();
            });
    }

    private <T extends Record> SelectConditionStep<T> filteringQuery(
        SelectConditionStep<T> select,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable List<FlowFilter> flows,
        @Nullable String query,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
        if (scope != null && !scope.containsAll(Arrays.stream(FlowScope.values()).toList())) {
            if (scope.contains(FlowScope.USER)) {
                select = select.and(field("namespace").ne(namespaceUtils.getSystemFlowNamespace()));
            } else if (scope.contains(FlowScope.SYSTEM)) {
                select = select.and(field("namespace").eq(namespaceUtils.getSystemFlowNamespace()));
            }
        }

        if (namespace != null) {
            if (flowId != null) {
                select = select.and(field("namespace").eq(namespace));
            } else {
                select = select.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
            }
        }

        if (flowId != null) {
            select = select.and(DSL.or(field("flow_id").eq(flowId)));
        }

        if (query != null || labels != null) {
            select = select.and(this.findCondition(query, labels));
        }

        if (triggerExecutionId != null) {
            select = select.and(field("trigger_execution_id").eq(triggerExecutionId));
        }

        if (childFilter != null) {
            if (childFilter.equals(ChildFilter.CHILD)) {
                select = select.and(field("trigger_execution_id").isNotNull());
            } else if (childFilter.equals(ChildFilter.MAIN)) {
                select = select.and(field("trigger_execution_id").isNull());
            }
        }

        if (flows != null) {
            select = select.and(DSL.or(
                flows
                    .stream()
                    .map(e -> field("namespace").eq(e.getNamespace())
                        .and(field("flow_id").eq(e.getId()))
                    )
                    .toList()
            ));
        }

        return select;
    }

    @Override
    public Map<String, ExecutionCountStatistics> executionCountsGroupedByNamespace(
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {

        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record3<String, String, Long>> selectCount = context
                    .select(NAMESPACE_FIELD, STATE_CURRENT_FIELD, DSL.count().cast(Long.class))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(START_DATE_FIELD.greaterOrEqual(finalStartDate.toOffsetDateTime()))
                    .and(START_DATE_FIELD.lessOrEqual(finalEndDate.toOffsetDateTime()));

                if (namespace != null) {
                    selectCount = selectCount.and(DSL.or(
                        NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%"),
                        NAMESPACE_FIELD.eq(namespace)
                    ));
                }

                Map<String, Result<Record3<String, String, Long>>> resultByNamespace = selectCount
                    .groupBy(STATE_CURRENT_FIELD, NAMESPACE_FIELD)
                    .fetch()
                    .intoGroups(NAMESPACE_FIELD);

                return resultByNamespace.entrySet().stream()
                    .map(entry -> {
                        Map<State.Type, Long> counts = entry.getValue()
                            .stream()
                            .map(r -> new AbstractMap.SimpleEntry<>(State.Type.valueOf(r.value2()), r.value3()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        return new AbstractMap.SimpleEntry<>(entry.getKey(), new ExecutionCountStatistics(counts));
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            });
    }

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable List<FlowFilter> flows,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    ) {
        List<Field<?>> fields = new ArrayList<>();

        fields.add(STATE_CURRENT_FIELD);
        fields.add(NAMESPACE_FIELD);

        if (!groupByNamespaceOnly) {
            fields.add(field("flow_id", String.class));
        }

        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        Results results = dailyStatisticsQuery(
            fields,
            query,
            tenantId,
            null,
            namespace,
            flowId,
            flows,
            finalStartDate,
            finalEndDate,
            null,
            null
        );

        return results
            .resultsOrRows()
            .getFirst()
            .result()
            .intoGroups(NAMESPACE_FIELD)
            .entrySet()
            .stream()
            .map(e -> {
                if (groupByNamespaceOnly) {
                    return new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        Map.of(
                            "*",
                            dailyStatisticsQueryMapRecord(
                                e.getValue(),
                                finalStartDate,
                                finalEndDate,
                                null
                            )
                        )
                    );
                } else {
                    return new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        e.getValue().intoGroups(field("flow_id", String.class))
                            .entrySet()
                            .stream()
                            .map(f -> new AbstractMap.SimpleEntry<>(
                                f.getKey(),
                                dailyStatisticsQueryMapRecord(
                                    f.getValue(),
                                    finalStartDate,
                                    finalEndDate,
                                    null
                                )
                            ))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    );
                }

            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<DailyExecutionStatistics> fillDate(List<DailyExecutionStatistics> results, ZonedDateTime startDate, ZonedDateTime endDate) {
        DateUtils.GroupType groupByType = DateUtils.groupByType(Duration.between(startDate, endDate));

        if (groupByType.equals(DateUtils.GroupType.MONTH)) {
            return fillDate(results, startDate, endDate, ChronoUnit.MONTHS, "YYYY-MM", groupByType.val());
        } else if (groupByType.equals(DateUtils.GroupType.WEEK)) {
            return fillDate(results, startDate, endDate, ChronoUnit.WEEKS, "YYYY-ww", groupByType.val());
        } else if (groupByType.equals(DateUtils.GroupType.DAY)) {
            return fillDate(results, startDate, endDate, ChronoUnit.DAYS, "YYYY-MM-DD", groupByType.val());
        } else if (groupByType.equals(DateUtils.GroupType.HOUR)) {
            return fillDate(results, startDate, endDate, ChronoUnit.HOURS, "YYYY-MM-DD HH", groupByType.val());
        } else {
            return fillDate(results, startDate, endDate, ChronoUnit.MINUTES, "YYYY-MM-DD HH:mm", groupByType.val());
        }
    }

    private static List<DailyExecutionStatistics> fillDate(
        List<DailyExecutionStatistics> results,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        ChronoUnit unit,
        String format,
        String groupByType
    ) {
        List<DailyExecutionStatistics> filledResult = new ArrayList<>();
        ZonedDateTime currentDate = startDate;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault());

        // Add one to the end date to include last intervals in the result
        String formattedEndDate = endDate.plus(1, unit).format(formatter);

        // Comparing date string formatted with only valuable part of the date
        // allow to avoid cases where latest interval was not included in the result
        // i.e if endDate is 18:15 and startDate 17:30, when reaching 18:30 it will not handle the 18th hours
        while (!currentDate.format(formatter).equals(formattedEndDate)) {
            String finalCurrentDate = currentDate.format(formatter);
            DailyExecutionStatistics dailyExecutionStatistics = results
                .stream()
                .filter(e -> formatter.format(e.getStartDate()).equals(finalCurrentDate))
                .findFirst()
                .orElse(DailyExecutionStatistics.builder()
                    .startDate(currentDate.toInstant())
                    .groupBy(groupByType)
                    .duration(DailyExecutionStatistics.Duration.builder().build())
                    .build()
                );

            filledResult.add(dailyExecutionStatistics);
            currentDate = currentDate.plus(1, unit);
        }

        return filledResult;
    }

    private DailyExecutionStatistics dailyExecutionStatisticsMap(Instant date, List<ExecutionStatistics> result, String groupByType) {
        long durationSum = result.stream().map(ExecutionStatistics::getDurationSum).mapToLong(value -> value).sum();
        long count = result.stream().map(ExecutionStatistics::getCount).mapToLong(value -> value).sum();

        DailyExecutionStatistics build = DailyExecutionStatistics.builder()
            .startDate(date)
            .groupBy(groupByType)
            .duration(DailyExecutionStatistics.Duration.builder()
                .avg(Duration.ofMillis(durationSum / count))
                .min(result.stream().map(ExecutionStatistics::getDurationMin).min(Long::compare).map(Duration::ofMillis).orElse(null))
                .max(result.stream().map(ExecutionStatistics::getDurationMax).max(Long::compare).map(Duration::ofMillis).orElse(null))
                .sum(Duration.ofMillis(durationSum))
                .count(count)
                .build()
            )
            .build();

        result.forEach(record -> build.getExecutionCounts()
            .compute(
                State.Type.valueOf(record.getStateCurrent()),
                (type, current) -> record.getCount()
            ));

        return build;
    }

    @Override
    public List<ExecutionCount> executionCounts(
        @Nullable String tenantId,
        List<Flow> flows,
        @Nullable List<State.Type> states,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<String> namespaces) {
        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        List<ExecutionCount> result = this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext dslContext = DSL.using(configuration);

                SelectConditionStep<?> select = dslContext
                    .select(List.of(
                        field("namespace"),
                        field("flow_id"),
                        DSL.count().as("count")
                    ))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(START_DATE_FIELD.greaterOrEqual(finalStartDate.toOffsetDateTime()));
                select = select.and(START_DATE_FIELD.lessOrEqual(finalEndDate.toOffsetDateTime()));

                if (!ListUtils.isEmpty(states)) {
                    select = select.and(this.statesFilter(states));
                }

                List<Condition> orConditions = new ArrayList<>();
                orConditions.addAll(ListUtils.emptyOnNull(flows)
                    .stream()
                    .map(flow -> DSL.and(
                        field("namespace").eq(flow.getNamespace()),
                        field("flow_id").eq(flow.getFlowId())
                    ))
                    .toList());

                orConditions.addAll(
                    ListUtils.emptyOnNull(namespaces)
                        .stream()
                        .map(np -> field("namespace").eq(np))
                        .toList()
                );

                // add flows filters
                select = select.and(DSL.or(orConditions));

                // map result to flow
                return select
                    .groupBy(List.of(
                        field("namespace"),
                        field("flow_id")
                    ))
                    .fetchMany()
                    .resultsOrRows()
                    .getFirst()
                    .result()
                    .stream()
                    .map(record -> new ExecutionCount(
                        record.getValue("namespace", String.class),
                        record.getValue("flow_id", String.class),
                        record.getValue("count", Long.class)
                    ))
                    .toList();
            });

        List<ExecutionCount> counts = new ArrayList<>();
        // fill missing with count at 0
        if (!ListUtils.isEmpty(flows)) {
            counts.addAll(flows
                .stream()
                .map(flow -> result
                    .stream()
                    .filter(executionCount -> executionCount.getNamespace().equals(flow.getNamespace()) &&
                        executionCount.getFlowId().equals(flow.getFlowId())
                    )
                    .findFirst()
                    .orElse(new ExecutionCount(
                        flow.getNamespace(),
                        flow.getFlowId(),
                        0L
                    ))
                )
                .toList());
        }

        if (!ListUtils.isEmpty(namespaces)) {
            Map<String, Long> groupedByNamespace = result.stream()
                .collect(Collectors.groupingBy(
                    ExecutionCount::getNamespace,
                    Collectors.summingLong(ExecutionCount::getCount)
                ));

            counts.addAll(groupedByNamespace.entrySet()
                .stream()
                .map(entry -> new ExecutionCount(entry.getKey(), null, entry.getValue()))
                .toList());
        }

        return counts;
    }

    public List<Execution> lastExecutions(
        @Nullable String tenantId,
        List<FlowFilter> flows
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Select<Record2<Object, Integer>> subquery = context
                    .select(
                        field("value"),
                        DSL.rowNumber().over(
                            DSL.partitionBy(
                                field("namespace"),
                                field("flow_id")
                            ).orderBy(field("end_date").desc())
                        ).as("row_num")
                    )
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("end_date").isNotNull())
                    .and(DSL.or(
                        flows
                            .stream()
                            .map(flow -> DSL.and(
                                field("namespace").eq(flow.getNamespace()),
                                field("flow_id").eq(flow.getId())
                            ))
                            .toList()
                    ));

                Table<Record2<Object, Integer>> cte = subquery.asTable("cte");

                SelectConditionStep<? extends Record1<?>> mainQuery = context
                    .select(cte.field("value"))
                    .from(cte)
                    .where(field("row_num").eq(1));
                return mainQuery.fetch().map(this.jdbcRepository::map);
            });
    }


    @Override
    public Execution save(Execution execution) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(execution);
        this.jdbcRepository.persist(execution, fields);

        return execution;
    }

    @Override
    public Execution save(DSLContext dslContext, Execution execution) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(execution);
        this.jdbcRepository.persist(execution, dslContext, fields);

        return execution;
    }

    @Override
    public int saveBatch(List<Execution> items) {
        if (ListUtils.isEmpty(items)) {
            return 0;
        }

        return this.jdbcRepository.persistBatch(items);
    }

    @Override
    public Execution update(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSL.using(configuration)
                    .update(this.jdbcRepository.getTable())
                    .set(this.jdbcRepository.persistFields((execution)))
                    .where(field("key").eq(execution.getId()))
                    .execute();

                return execution;
            });
    }

    @SneakyThrows
    @Override
    public Execution delete(Execution execution) {
        Optional<Execution> revision = this.findById(execution.getTenantId(), execution.getId());
        if (revision.isEmpty()) {
            throw new IllegalStateException("Execution " + execution.getId() + " doesn't exists");
        }

        Execution deleted = execution.toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        this.jdbcRepository.persist(deleted, fields);

        executionQueue().emit(deleted);

        eventPublisher.publishEvent(new CrudEvent<>(deleted, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public Integer purge(Execution execution) {
        return this.jdbcRepository.delete(execution);
    }

    public Executor lock(String executionId, Function<Pair<Execution, ExecutorState>, Pair<Executor, ExecutorState>> function) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectForUpdateOfStep<Record1<Object>> from = context
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(executionId))
                    .and(this.defaultFilter())
                    .forUpdate();

                Optional<Execution> execution = this.jdbcRepository.fetchOne(from);

                // not ready for now, skip and wait for a first state
                if (execution.isEmpty()) {
                    return null;
                }

                ExecutorState executorState = executorStateStorage.get(context, execution.get());
                Pair<Executor, ExecutorState> pair = function.apply(Pair.of(execution.get(), executorState));

                if (pair != null) {
                    this.jdbcRepository.persist(pair.getKey().getExecution(), context, null);
                    this.executorStateStorage.save(context, pair.getRight());

                    return pair.getKey();
                }

                return null;
            });
    }

    @Override
    public Function<String, String> sortMapping() throws IllegalArgumentException {
        Map<String, String> mapper = Map.of(
            "id", "id",
            "state.startDate", "start_date",
            "state.endDate", "end_date",
            "state.duration", "state_duration",
            "namespace", "namespace",
            "flowId", "flow_id",
            "state.current", "state_current"
        );

        return mapper::get;
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Executions.Fields, ? extends ColumnDescriptor<Executions.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<String, ? extends ColumnDescriptor<Executions.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
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

                List<? extends ColumnDescriptor<Executions.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream()
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

    @Override
    protected <F extends Enum<F>> Field<?> columnToField(ColumnDescriptor<?> column, Map<F, String> fieldsMapping) {
        if (column.getField() == null) {
            return null;
        }
        Field<?> field = field(fieldsMapping.get(column.getField()));
        if (field.getName().equals(STATE_CURRENT_FIELD.getName())) {
            return STATE_CURRENT_FIELD;
        } else if (field.getName().equals(NAMESPACE_FIELD.getName())) {
            return NAMESPACE_FIELD;
        } else if (field.getName().equals(START_DATE_FIELD.getName())) {
            return START_DATE_FIELD;
        }
        else if (field.getName().equals(fieldsMapping.get(Executions.Fields.DURATION))) {
            return DSL.field("{0} / 1000", Long.class, field);
        }
        return field;
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, DataFilter<F, ? extends ColumnDescriptor<F>> descriptors, Map<F, String> fieldsMapping) {
        if (!ListUtils.isEmpty(descriptors.getWhere())) {
            // Check if descriptors contain a filter of type Executions.Fields.STATE and apply the custom filter "statesFilter" if present
            List<In<Executions.Fields>> stateFilters = descriptors.getWhere().stream()
                .filter(descriptor -> descriptor.getField().equals(Executions.Fields.STATE) && descriptor instanceof In)
                .map(descriptor -> (In<Executions.Fields>) descriptor)
                .toList();

            if (!stateFilters.isEmpty()) {
                selectConditionStep = selectConditionStep.and(
                    statesFilter(stateFilters.stream()
                        .flatMap(stateFilter -> stateFilter.getValues().stream())
                        .map(value -> State.Type.valueOf(value.toString()))
                        .toList())
                );
            }

            // Check if descriptors contain a filter of type EXECUTIONS.Fields.LABELS and apply the findCondition() method if present
            List<Contains<Executions.Fields>> labelFilters = descriptors.getWhere().stream()
                .filter(descriptor -> descriptor.getField().equals(Executions.Fields.LABELS) && descriptor instanceof Contains<F>)
                .map(descriptor -> (Contains<Executions.Fields>) descriptor)
                .toList();

            if (!labelFilters.isEmpty()) {
                Map<String, String> mergedMap = new HashMap<>();

                labelFilters.forEach(labelFilter -> {
                    Map<String, String> currentMap = (Map<String, String>) labelFilter.getValue();
                    mergedMap.putAll(currentMap);
                });

                selectConditionStep = selectConditionStep.and(findCondition(null, mergedMap));
            }

            // Remove the state filters from descriptors
            List<AbstractFilter<F>> remainingFilters = descriptors.getWhere().stream()
                .filter(descriptor -> !descriptor.getField().equals(Executions.Fields.STATE) || !(descriptor instanceof In)) // Filter state
                .filter(descriptor -> !descriptor.getField().equals(Executions.Fields.LABELS) || !(descriptor instanceof Contains<F>)) // Filter labels
                .toList();



            // Use the generic method addFilters with the remaining filters
            return filterService.addFilters(selectConditionStep, fieldsMapping, remainingFilters);
        } else {
            return selectConditionStep;
        }
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
