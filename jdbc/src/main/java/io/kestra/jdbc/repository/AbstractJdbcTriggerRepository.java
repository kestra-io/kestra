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
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.ScheduleContextInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.runner.JdbcQueueIndexerInterface;
import io.kestra.jdbc.runner.JdbcSchedulerContext;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.ITriggers;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.micronaut.data.model.Pageable;
import lombok.Getter;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcCrudRepository<Trigger> implements TriggerRepositoryInterface, JdbcQueueIndexerInterface<Trigger> {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");

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
        return Triggers.Fields.NEXT_EXECUTION_DATE;
    }

    public AbstractJdbcTriggerRepository(io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository,
                                         QueueService queueService,
                                         JdbcFilterService filterService) {
        super(jdbcRepository, queueService);

        this.filterService = filterService;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return findByUid(trigger.uid());
    }

    @Override
    public Optional<Trigger> findByUid(String uid) {
        return findOne(DSL.trueCondition(), field("key").eq(uid));
    }

    public List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContextInterface) {
        JdbcSchedulerContext jdbcSchedulerContext = (JdbcSchedulerContext) scheduleContextInterface;

        return jdbcSchedulerContext.getContext()
            .select(field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                (field("next_execution_date").lessThan(toNextExecutionTime(now))
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
                    (field("next_execution_date").lessThan(toNextExecutionTime(now))
                        // we check for null for backwards compatibility
                        .or(field("next_execution_date").isNull()))
                        .and(field("execution_id").isNotNull())
                )
                .orderBy(field("next_execution_date").asc())
                .fetch()
                .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class))));
    }

    protected Temporal toNextExecutionTime(ZonedDateTime now) {
        return now.toOffsetDateTime();
    }

    public Trigger save(Trigger trigger, ScheduleContextInterface scheduleContextInterface) {
        JdbcSchedulerContext jdbcSchedulerContext = (JdbcSchedulerContext) scheduleContextInterface;

        save(jdbcSchedulerContext.getContext(), trigger);

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
        var condition = filter(filters, fieldsMapping.get(dateFilterField()), Resource.TRIGGER);
        return findPage(pageable, tenantId, condition);
    }


    @Override
    public ArrayListTotal<Trigger> find(Pageable pageable, String query, String tenantId, String namespace, String flowId, String workerId) {
        var condition = this.fullTextCondition(query).and(this.defaultFilter());

        if (namespace != null) {
            condition = condition.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
        }

        if (flowId != null) {
            condition = condition.and(field("flow_id").eq(flowId));
        }

        if (workerId != null) {
            condition = condition.and(field("worker_id").eq(workerId));
        }

        return findPage(pageable, tenantId, condition);
    }

    @Override
    public Flux<Trigger> findAsync(String tenantId, List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return findAllAsync(tenantId);
        }
        Condition condition = this.filter(filters, fieldsMapping.get(dateFilterField()), Resource.TRIGGER);
        return findAsync(defaultFilter(tenantId), condition);
    }

    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    @Override
    protected Condition findQueryCondition(String query) {
        return fullTextCondition(query);
    }

    @Override
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
