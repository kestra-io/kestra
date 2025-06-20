package io.kestra.jdbc.repository;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.runner.JdbcQueueIndexerInterface;
import io.kestra.jdbc.runner.JdbcSchedulerContext;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.ITriggers;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import lombok.Getter;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcRepository implements TriggerRepositoryInterface, JdbcQueueIndexerInterface<Trigger> {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");

    protected io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository;

    private final JdbcFilterService filterService;

    @Getter
    private final Map<Triggers.Fields, String> fieldsMapping = Map.of(
        Triggers.Fields.ID, "key",
        Triggers.Fields.NAMESPACE, "namespace",
        Triggers.Fields.FLOW_ID, "flow_id",
        Triggers.Fields.TRIGGER_ID, "trigger_id",
        Triggers.Fields.EXECUTION_ID, "execution_id",
        Triggers.Fields.NEXT_EXECUTION_DATE, "next_execution_date",
        Triggers.Fields.WORKER_ID, "worker_id"
    );

    @Override
    public Set<Triggers.Fields> dateFields() {
        return Set.of();
    }

    @Override
    public Triggers.Fields dateFilterField() {
        return null;
    }

    public AbstractJdbcTriggerRepository(io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository,
                                         JdbcFilterService filterService) {
        this.jdbcRepository = jdbcRepository;

        this.filterService = filterService;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(trigger.uid()));

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("execution_id").eq(execution.getId())
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<Trigger> findAll(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Trigger> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public int count(@Nullable String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .selectCount()
                .from(this.jdbcRepository.getTable())
                .where(this.defaultFilter(tenantId))
                .fetchOne(0, int.class));
    }

    @Override
    public int countForNamespace(@Nullable String tenantId, @Nullable String namespace) {
        if (namespace == null) return count(tenantId);

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .selectCount()
                .from(this.jdbcRepository.getTable())
                .where(this.defaultFilter(tenantId))
                .and(DSL.or(
                    NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%"),
                    NAMESPACE_FIELD.eq(namespace)
                ))
                .fetchOne(0, int.class));
    }

    public List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContextInterface) {
        JdbcSchedulerContext jdbcSchedulerContext = (JdbcSchedulerContext) scheduleContextInterface;

        return jdbcSchedulerContext.getContext()
            .select(field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                (field("next_execution_date").lessThan(now.toOffsetDateTime())
                    // we check for null for backwards compatibility
                    .or(field("next_execution_date").isNull()))
                    .and(field("execution_id").isNull())
            )
            .orderBy(field("next_execution_date").asc())
            .forUpdate()
            .skipLocked()
            .fetch()
            .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)));
    }

    public List<Trigger> findByNextExecutionDateReadyButLockedTriggers(ZonedDateTime now) {
        return this.jdbcRepository.getDslContextWrapper()
            .transactionResult(configuration -> DSL.using(configuration)
                .select(field("value"))
                .from(this.jdbcRepository.getTable())
                .where(
                    (field("next_execution_date").lessThan(now.toOffsetDateTime())
                        // we check for null for backwards compatibility
                        .or(field("next_execution_date").isNull()))
                        .and(field("execution_id").isNotNull())
                )
                .orderBy(field("next_execution_date").asc())
                .fetch()
                .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class))));
    }

    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) {
        JdbcSchedulerContext jdbcSchedulerContext = (JdbcSchedulerContext) scheduleContextInterface;

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, jdbcSchedulerContext.getContext(), fields);

        return trigger;
    }

    @Override
    public Trigger save(Trigger trigger) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, fields);

        return trigger;
    }

    @Override
    public Trigger save(DSLContext dslContext, Trigger trigger) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, dslContext, fields);

        return trigger;
    }

    public Trigger create(Trigger trigger) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSL.using(configuration)
                    .insertInto(this.jdbcRepository.getTable())
                    .set(AbstractJdbcRepository.field("key"), this.jdbcRepository.key(trigger))
                    .set(this.jdbcRepository.persistFields(trigger))
                    .execute();

                return trigger;
            });
    }

    @Override
    public void delete(Trigger trigger) {
        this.jdbcRepository.delete(trigger);
    }

    @Override
    public Trigger update(Trigger trigger) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSL.using(configuration)
                    .update(this.jdbcRepository.getTable())
                    .set(this.jdbcRepository.persistFields((trigger)))
                    .where(field("key").eq(trigger.uid()))
                    .execute();

                return trigger;
            });
    }

    // Allow to update a trigger from a flow & an abstract trigger
    // using forUpdate to avoid the lastTrigger to be updated by another thread
    // before doing the update
    public Trigger update(Flow flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Optional<Trigger> lastTrigger = this.jdbcRepository.fetchOne(DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(Trigger.uid(flow, abstractTrigger)))
                    .forUpdate()
                );

                Trigger updatedTrigger = Trigger.of(flow, abstractTrigger, conditionContext, lastTrigger);

                DSL.using(configuration)
                    .update(this.jdbcRepository.getTable())
                    .set(this.jdbcRepository.persistFields(updatedTrigger))
                    .where(field("key").eq(updatedTrigger.uid()))
                    .execute();

                return updatedTrigger;
            });
    }

    @Override
    public Trigger lock(String triggerUid, Function<Trigger, Trigger> function) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Optional<Trigger> optionalTrigger = this.jdbcRepository.fetchOne(context.select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("key").eq(triggerUid)
                    ).forUpdate());

                if (optionalTrigger.isPresent()) {
                    Trigger trigger = function.apply(optionalTrigger.get());

                    this.save(context, trigger);
                    return trigger;
                }

                return null;
            });
    }
    @Override
    public ArrayListTotal<Trigger> find(Pageable pageable, String tenantId, List<QueryFilter> filters) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<?> select = generateSelect(context, tenantId, filters);
                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    private SelectConditionStep<?> generateSelect(DSLContext context, String tenantId, List<QueryFilter> filters){
        SelectConditionStep<?> select = context
            .select(field("value"))
            .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter(tenantId));

        return filter(select, filters, "next_execution_date", Resource.TRIGGER);
    }

    @Override
    public ArrayListTotal<Trigger> find(Pageable pageable, String query, String tenantId, String namespace, String flowId, String workerId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.fullTextCondition(query))
                    .and(this.defaultFilter(tenantId));

                if (namespace != null) {
                    select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
                }

                if (flowId != null) {
                    select.and(field("flow_id").eq(flowId));
                }

                if (workerId != null) {
                    select.and(field("worker_id").eq(workerId));
                }
                select.and(this.defaultFilter());

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Trigger> find(String tenantId, List<QueryFilter> filters) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    DSLContext context = DSL.using(configuration);
                    SelectConditionStep<?> select = generateSelect(context, tenantId, filters);

                    select.fetch()
                    .map(this.jdbcRepository::map)
                    .forEach(emitter::next);

                    emitter.complete();

                }),
            FluxSink.OverflowStrategy.BUFFER
        );

    }

    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    protected Condition findQueryCondition(String query) {
        return fullTextCondition(query);
    }

    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    @Override
    public Function<String, String> sortMapping() throws IllegalArgumentException {
        Map<String, String> mapper = Map.of(
            "flowId", "flow_id",
            "triggerId", "trigger_id",
            "executionId", "execution_id",
            "nextExecutionDate", "next_execution_date"
        );

        return s -> mapper.getOrDefault(s, s);
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Triggers.Fields, ? extends ColumnDescriptor<Triggers.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<String, ? extends ColumnDescriptor<Triggers.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
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
                selectConditionStep = where(selectConditionStep, filterService, descriptors.getWhere(), fieldsMapping);

                List<? extends ColumnDescriptor<Triggers.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream()
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



    public Double fetchValue(String tenantId, DataFilterKPI<ITriggers.Fields, ? extends ColumnDescriptor<ITriggers.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate, boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<ITriggers.Fields> columnDescriptor = dataFilter.getColumns();
            String columnKey = this.getFieldsMapping().get(columnDescriptor.getField());
            Field<?> field = columnToField(columnDescriptor, getFieldsMapping());
            if (columnDescriptor.getAgg() != null) {
                field = filterService.buildAggregation(field, columnDescriptor.getAgg());
            }

            List<AbstractFilter<ITriggers.Fields>> filters = new ArrayList<>(ListUtils.emptyOnNull(dataFilter.getWhere()));
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


    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);
}
