package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.core.runners.ExecutorState;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Singleton
public abstract class AbstractJdbcExecutionRepository extends AbstractJdbcRepository implements ExecutionRepositoryInterface, JdbcIndexerInterface<Execution> {
    protected io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository;
    protected AbstractJdbcExecutorStateStorage executorStateStorage;

    public AbstractJdbcExecutionRepository(io.kestra.jdbc.AbstractJdbcRepository<Execution> jdbcRepository, AbstractJdbcExecutorStateStorage executorStateStorage) {
        this.jdbcRepository = jdbcRepository;
        this.executorStateStorage = executorStateStorage;
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

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        field("value")
                    )
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                if (flowId != null && namespace != null) {
                    select = select.and(field("namespace").eq(namespace));
                    select = select.and(field("flow_id").eq(flowId));
                } else if (namespace != null) {
                    select = select.and(field("namespace").likeIgnoreCase(namespace + "%"));
                }

                if (startDate != null) {
                    select = select.and(field("start_date").greaterOrEqual(startDate.toOffsetDateTime()));
                }

                if (endDate != null) {
                    select = select.and(field("end_date").lessOrEqual(endDate.toOffsetDateTime()));
                }

                if (state != null) {
                    select = select.and(field("state_current")
                        .in(state.stream().map(Enum::name).collect(Collectors.toList())));
                }

                if (query != null) {
                    select.and(this.findCondition(query));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
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
        @Nullable List<State.Type> states
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
        boolean isTaskRun
    ) {
        if (isTaskRun) {
            throw new UnsupportedOperationException();
        }

        Results results = dailyStatisticsQuery(
            List.of(
                DSL.date(field("start_date", Date.class)).as("start_date"),
                field("state_current", String.class)
            ),
            query,
            startDate,
            endDate
        );

        return dailyStatisticsQueryMapRecord(
            results.resultsOrRows()
                .get(0)
                .result(),
            startDate,
            endDate
        );
    }

    private static List<DailyExecutionStatistics> dailyStatisticsQueryMapRecord(Result<Record> records, ZonedDateTime startDate, ZonedDateTime endDate) {
        return fillMissingDate(
            records.intoGroups(field("start_date", java.sql.Date.class)),
            startDate,
            endDate
        )
            .entrySet()
            .stream()
            .map(dateResultEntry -> dailyExecutionStatisticsMap(dateResultEntry.getKey(), dateResultEntry.getValue()))
            .sorted(Comparator.comparing(DailyExecutionStatistics::getStartDate))
            .collect(Collectors.toList());
    }

    private Results dailyStatisticsQuery(List<Field<?>> fields, String query, ZonedDateTime startDate, ZonedDateTime endDate) {
        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        List<Field<?>> selectFields = new ArrayList<>(fields);
        selectFields.addAll(List.of(
            DSL.count().as("count"),
            DSL.min(field("state_duration", Long.class)).as("duration_min"),
            DSL.max(field("state_duration", Long.class)).as("duration_max"),
            DSL.sum(field("state_duration", Long.class)).as("duration_sum")
        ));

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

                if (query != null) {
                    select.and(this.findCondition(query));
                }

                List<Field<?>> groupFields = new ArrayList<>();
                if (context.configuration().dialect() != SQLDialect.H2) {
                    for (int i = 1; i <= fields.size(); i++) {
                        groupFields.add(DSL.field(String.valueOf(i)));
                    }
                } else {
                    groupFields = fields;
                }

                SelectHavingStep<?> finalQuery = select
                    .groupBy(groupFields);

                return finalQuery.fetchMany();
            });
    }


    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    ) {
        List<Field<?>> fields = new ArrayList<>();

        fields.add(DSL.date(field("start_date", Date.class)).as("start_date"));
        fields.add(field("state_current", String.class));
        fields.add(field("namespace", String.class));

        if (!groupByNamespaceOnly) {
            fields.add(field("flow_id", String.class));
        }

        Results results = dailyStatisticsQuery(
            fields,
            query,
            startDate,
            endDate
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
                                    endDate
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
                                    endDate
                                )
                            ))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    );
                }

            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<java.sql.Date, Result<Record>> fillMissingDate(Map<java.sql.Date, Result<Record>> result, ZonedDateTime startDate, ZonedDateTime endDate) {
        LocalDate compare = startDate.toLocalDate();

        while (compare.compareTo(endDate.toLocalDate()) < 0) {
            java.sql.Date sqlDate = java.sql.Date.valueOf(compare);

            if (!result.containsKey(sqlDate)) {
                result.put(sqlDate, null);
            }
            compare = compare.plus(1, ChronoUnit.DAYS);
        }

        return result;
    }

    private static DailyExecutionStatistics dailyExecutionStatisticsMap(java.sql.Date date, @Nullable Result<Record> result) {
        if (result == null) {
            return DailyExecutionStatistics.builder()
                .startDate(date.toLocalDate())
                .duration(DailyExecutionStatistics.Duration.builder().build())
                .build();
        }

        long durationSum = result.getValues("duration_sum", Long.class).stream().mapToLong(value -> value).sum();
        long count = result.getValues("count", Long.class).stream().mapToLong(value -> value).sum();

        DailyExecutionStatistics build = DailyExecutionStatistics.builder()
            .startDate(date.toLocalDate())
            .duration(DailyExecutionStatistics.Duration.builder()
                 .avg(Duration.ofMillis(durationSum / count))
                .min(result.getValues("duration_min", Long.class).stream().min(Long::compare).map(Duration::ofMillis).orElse(null))
                .max(result.getValues("duration_min", Long.class).stream().max(Long::compare).map(Duration::ofMillis).orElse(null))
                .sum(Duration.ofMillis(durationSum))
                .count(count)
                .build()
            )
            .build();

        result.forEach(record -> build.getExecutionCounts()
            .compute(
                State.Type.valueOf(record.get("state_current", String.class)),
                (type, current) -> record.get("count", Integer.class).longValue()
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

    public Executor lock(String executionId, Function<Pair<Execution, ExecutorState>, Pair<Executor, ExecutorState>> function) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectForUpdateOfStep<Record1<Object>> from = context
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(executionId))
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
