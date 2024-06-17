package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.ExecutionStatistics;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorState;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.Results;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateOfStep;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public abstract class AbstractJdbcExecutionRepository extends AbstractJdbcRepository implements ExecutionRepositoryInterface, JdbcIndexerInterface<Execution> {
    protected final io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;
    private final ApplicationContext applicationContext;
    protected final AbstractJdbcExecutorStateStorage executorStateStorage;

    private QueueInterface<Execution> executionQueue;

    @SuppressWarnings("unchecked")
    public AbstractJdbcExecutionRepository(
        io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository,
        ApplicationContext applicationContext,
        AbstractJdbcExecutorStateStorage executorStateStorage
    ) {
        this.jdbcRepository = jdbcRepository;
        this.executorStateStorage = executorStateStorage;
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);

        // we inject ApplicationContext in order to get the ExecutionQueue lazy to avoid StackOverflowError
        this.applicationContext = applicationContext;
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

    /** {@inheritDoc} **/
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

                    select.fetch()
                        .map(this.jdbcRepository::map)
                        .forEach(emitter::next);
                    emitter.complete();
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    @Override
    public Optional<Execution> findById(String tenantId, String id, boolean allowDeleted) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId, allowDeleted))
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
                    namespace,
                    flowId,
                    startDate,
                    endDate,
                    state,
                    labels,
                    triggerExecutionId,
                    childFilter
                );

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public Flux<Execution> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
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
                        namespace,
                        flowId,
                        startDate,
                        endDate,
                        state,
                        labels,
                        triggerExecutionId,
                        childFilter
                    );

                    select.fetch()
                        .map(this.jdbcRepository::map)
                        .forEach(emitter::next);

                    emitter.complete();
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    private SelectConditionStep<Record1<Object>> findSelect(
        DSLContext context,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
        SelectConditionStep<Record1<Object>> select = context
            .select(
                field("value")
            )
            .hint(context.configuration().dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter(tenantId));

        select = filteringQuery(select, namespace, flowId, null, query, labels, triggerExecutionId, childFilter);

        if (startDate != null) {
            select = select.and(field("start_date").greaterOrEqual(startDate.toOffsetDateTime()));
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
    public Integer maxTaskRunSetting() {
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

        Results results = dailyStatisticsQueryForAllTenants(
            List.of(
                field("state_current", String.class)
            ),
            query,
            namespace,
            flowId,
            null,
            startDate,
            endDate,
            groupBy
        );

        return dailyStatisticsQueryMapRecord(
            results.resultsOrRows()
                .getFirst()
                .result(),
            startDate,
            endDate,
            groupBy
        );
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String tenantId,
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

        Results results = dailyStatisticsQuery(
            List.of(
                field("state_current", String.class)
            ),
            query,
            tenantId,
            namespace,
            flowId,
            null,
            startDate,
            endDate,
            groupBy
        );

        return dailyStatisticsQueryMapRecord(
            results.resultsOrRows()
                .getFirst()
                .result(),
            startDate,
            endDate,
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
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy
    ) {
        return dailyStatisticsQuery(
            this.defaultFilter(),
            fields,
            query,
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            groupBy
        );
    }

    private Results dailyStatisticsQuery(
        List<Field<?>> fields,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        List<FlowFilter> flows,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy
    ) {
        return dailyStatisticsQuery(
            this.defaultFilter(tenantId),
            fields,
            query,
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            groupBy
        );
    }

    private Results dailyStatisticsQuery(
        Condition defaultFilter,
        List<Field<?>> fields,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        List<FlowFilter> flows,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy
    ) {
        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        List<Field<?>> dateFields = new ArrayList<>(groupByFields(Duration.between(finalStartDate, finalEndDate), "start_date", groupBy));
        List<Field<?>> selectFields = new ArrayList<>(fields);
        selectFields.addAll(List.of(
            DSL.count().as("count"),
            DSL.min(field("state_duration", Long.class)).as("duration_min"),
            DSL.max(field("state_duration", Long.class)).as("duration_max"),
            DSL.sum(field("state_duration", Long.class)).as("duration_sum")
        ));
        selectFields.addAll(dateFields);

        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<?> select = context
                    .select(selectFields)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter)
                    .and(field("start_date").greaterOrEqual(finalStartDate.toOffsetDateTime()))
                    .and(field("start_date").lessOrEqual(finalEndDate.toOffsetDateTime()));

                select = filteringQuery(select, namespace, flowId, flows, query, null, null, null);

                List<Field<?>> groupFields = new ArrayList<>(fields);

                groupFields.addAll(dateFields);

                SelectHavingStep<?> finalQuery = select
                    .groupBy(groupFields);

                return finalQuery.fetchMany();
            });
    }

    private <T extends Record> SelectConditionStep<T> filteringQuery(
        SelectConditionStep<T> select,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable List<FlowFilter> flows,
        @Nullable String query,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
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

        fields.add(field("state_current", String.class));
        fields.add(field("namespace", String.class));

        if (!groupByNamespaceOnly) {
            fields.add(field("flow_id", String.class));
        }

        Results results = dailyStatisticsQuery(
            fields,
            query,
            tenantId,
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            null
        );

        return results
            .resultsOrRows()
            .getFirst()
            .result()
            .intoGroups(field("namespace", String.class))
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
                                startDate,
                                endDate,
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
                                    startDate,
                                    endDate,
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
        @Nullable ZonedDateTime endDate
    ) {
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

                if (startDate != null) {
                    select = select.and(field("start_date").greaterOrEqual(finalStartDate.toOffsetDateTime()));
                }

                if (endDate != null) {
                    select = select.and(field("end_date").lessOrEqual(finalEndDate.toOffsetDateTime()));
                }

                if (states != null) {
                    select = select.and(this.statesFilter(states));
                }

                // add flow & namespace filters
                select = select.and(DSL.or(
                    flows
                        .stream()
                        .map(flow -> DSL.and(
                            field("namespace").eq(flow.getNamespace()),
                            field("flow_id").eq(flow.getFlowId())
                        ))
                        .toList()
                ));

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

        // fill missing with count at 0
        return flows
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
            .toList();
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
        Optional<Execution> revision = this.findById(execution.getTenantId(), execution.getId(), false);
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
}
