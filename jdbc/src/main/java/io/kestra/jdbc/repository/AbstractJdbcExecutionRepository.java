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
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
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

    @Override
    public Optional<Execution> findById(String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(field("key").eq(id));

                return this.jdbcRepository.fetchOne(from);
            });
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    protected Condition statesFilter(List<State.Type> state) {
        return field("state_current")
            .in(state.stream().map(Enum::name).collect(Collectors.toList()));
    }

    @Override
    public ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.findSelect(
                    context,
                    query,
                    namespace,
                    flowId,
                    startDate,
                    endDate,
                    state,
                    labels
                );

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public Flowable<Execution> find(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels
    ) {
        return Flowable.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    DSLContext context = DSL.using(configuration);

                    SelectConditionStep<Record1<Object>> select = this.findSelect(
                        context,
                        query,
                        namespace,
                        flowId,
                        startDate,
                        endDate,
                        state,
                        labels
                    );

                    select.fetch()
                        .map(this.jdbcRepository::map)
                        .forEach(emitter::onNext);

                    emitter.onComplete();
                }),
            BackpressureStrategy.BUFFER
        );
    }

    private SelectConditionStep<Record1<Object>> findSelect(
        DSLContext context,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels
    ) {
        SelectConditionStep<Record1<Object>> select = context
            .select(
                field("value")
            )
            .hint(context.configuration().dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter());

        select = filteringQuery(select, namespace, flowId, null, query, labels);

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
    public ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(id));

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> states,
        @Nullable Map<String, String> labels
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer maxTaskRunSetting() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
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

        Results results = dailyStatisticsQuery(
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
                .get(0)
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
            .collect(Collectors.toList()), startDate, endDate);
    }

    private Results dailyStatisticsQuery(
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
                    .where(this.defaultFilter())
                    .and(field("start_date").greaterOrEqual(finalStartDate.toOffsetDateTime()))
                    .and(field("start_date").lessOrEqual(finalEndDate.toOffsetDateTime()));

                select = filteringQuery(select, namespace, flowId, flows, query, null);

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
        @Nullable Map<String, String> labels) {
        if (flowId != null && namespace != null) {
            select = select.and(field("namespace").eq(namespace));
            select = select.and(field("flow_id").eq(flowId));
        } else if (namespace != null) {
            select = select.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
        }

        if (query != null || labels != null) {
            select = select.and(this.findCondition(query, labels));
        }

        if (flows != null) {
            select = select.and(DSL.or(
                flows
                    .stream()
                    .map(e -> field("namespace").eq(e.getNamespace())
                        .and(field("flow_id").eq(e.getId()))
                    )
                    .collect(Collectors.toList())
            ));
        }

        return select;
    }

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
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
            namespace,
            flowId,
            flows,
            startDate,
            endDate,
            null
        );

        return results
            .resultsOrRows()
            .get(0)
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
        }  else if (groupByType.equals(DateUtils.GroupType.HOUR)) {
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

    private DailyExecutionStatistics dailyExecutionStatisticsMap(Instant date, List<ExecutionStatistics>  result, String groupByType) {
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
                    .where(this.defaultFilter())
                    .and(field("start_date").greaterOrEqual(finalStartDate.toOffsetDateTime()))
                    .and(field("end_date").lessOrEqual(finalEndDate.toOffsetDateTime()));

                if (states != null) {
                    select = select.and(field("state_current")
                        .in(states.stream().map(Enum::name).collect(Collectors.toList())));
                }

                // add flow & namespace filters
                select = select.and(DSL.or(
                    flows
                        .stream()
                        .map(flow -> DSL.and(
                            field("namespace").eq(flow.getNamespace()),
                            field("flow_id").eq(flow.getFlowId())
                        ))
                        .collect(Collectors.toList())
                ));

                // map result to flow
                return select
                    .groupBy(List.of(
                        field("namespace"),
                        field("flow_id")
                    ))
                    .fetchMany()
                    .resultsOrRows()
                    .get(0)
                    .result()
                    .stream()
                    .map(records -> new ExecutionCount(
                        records.getValue("namespace", String.class),
                        records.getValue("flow_id", String.class),
                        records.getValue("count", Long.class)
                    ))
                    .collect(Collectors.toList());
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
            .collect(Collectors.toList());
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

    @SneakyThrows
    @Override
    public Execution delete(Execution execution) {
        Optional<Execution> revision = this.findById(execution.getId());
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
