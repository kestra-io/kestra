package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Singleton
public abstract class AbstractExecutionRepository extends AbstractRepository implements ExecutionRepositoryInterface {
    protected AbstractJdbcRepository<Execution> jdbcRepository;

    public AbstractExecutionRepository(AbstractJdbcRepository<Execution> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<Execution> findById(String id) {
        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(DSL.field("id").eq(id));

                return this.jdbcRepository.fetchOne(from);
            });
    }

    public ArrayListTotal<Execution> find(String query, Pageable pageable, List<State.Type> state) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        DSL.field("value")
                    )
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                if (state != null) {
                    select = select.and(DSL.field("state_current")
                        .in(state.stream().map(Enum::name).collect(Collectors.toList())));
                }

                if (query != null && !query.equals("*")) {
                    select.and(this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        DSL.field("value")
                    )
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(DSL.field("namespace").eq(namespace))
                    .and(DSL.field("flow_id").eq(id));

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(String query, Pageable pageable, List<State.Type> state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer maxTaskRunSetting() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(String query, ZonedDateTime startDate, ZonedDateTime endDate, boolean isTaskRun) {
        if (isTaskRun) {
            throw new UnsupportedOperationException();
        }

        Results results = dailyStatisticsQuery(
            List.of(
                DSL.date(DSL.field("start_date", Date.class)).as("start_date"),
                DSL.field("state_current", String.class)
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
            records.intoGroups(DSL.field("start_date", java.sql.Date.class)),
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
            DSL.min(DSL.field("state_duration", Long.class)).as("duration_min"),
            DSL.max(DSL.field("state_duration", Long.class)).as("duration_max"),
            DSL.sum(DSL.field("state_duration", Long.class)).as("duration_sum")
        ));

        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<?> select = DSL
                    .using(configuration)
                    .select(selectFields)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(DSL.field("start_date").greaterOrEqual(finalStartDate.toInstant()))
                    .and(DSL.field("start_date").lessOrEqual(finalEndDate.toInstant()));

                if (query != null && !query.equals("*")) {
                    select = select.and(this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
                }

                List<Field<?>> groupFields = new ArrayList<>();
                for (int i = 1; i <= fields.size(); i++) {
                    groupFields.add(DSL.field(String.valueOf(i)));
                }

                return select
                    .groupBy(groupFields)
                    .fetchMany();
            });
    }

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(String query, ZonedDateTime startDate, ZonedDateTime endDate, boolean groupByNamespaceOnly) {
        List<Field<?>> fields = new ArrayList<>();

        fields.add(DSL.date(DSL.field("start_date", Date.class)).as("start_date"));
        fields.add(DSL.field("state_current", String.class));
        fields.add(DSL.field(DSL.field("namespace", String.class)));

        if (!groupByNamespaceOnly) {
            fields.add(DSL.field("flow_id", String.class));
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
            .intoGroups(DSL.field("namespace", String.class))
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
                        e.getValue().intoGroups(DSL.field("flow_id", String.class))
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
    public List<ExecutionCount> executionCounts(List<Flow> flows, String query, ZonedDateTime startDate, ZonedDateTime endDate) {
        ZonedDateTime finalStartDate = startDate == null ? ZonedDateTime.now().minusDays(30) : startDate;
        ZonedDateTime finalEndDate = endDate == null ? ZonedDateTime.now() : endDate;

        List<ExecutionCount> result = this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<?> select = this.jdbcRepository
                    .getDslContext()
                    .select(List.of(
                        DSL.field("namespace"),
                        DSL.field("flow_id"),
                        DSL.count().as("count")
                    ))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(DSL.field("start_date").greaterOrEqual(finalStartDate.toInstant()))
                    .and(DSL.field("start_date").lessOrEqual(finalEndDate.toInstant()));

                if (query != null && !query.equals("*")) {
                    select = select.and(this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
                }

                // add flow & namespace filters
                select = select.and(DSL.or(
                    flows
                        .stream()
                        .map(flow -> DSL.and(
                            DSL.field("namespace").eq(flow.getNamespace()),
                            DSL.field("flow_id").eq(flow.getFlowId())
                        ))
                        .collect(Collectors.toList())
                ));

                // map result to flow
                return select
                    .groupBy(List.of(
                        DSL.field("1"),
                        DSL.field("2")
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
}
