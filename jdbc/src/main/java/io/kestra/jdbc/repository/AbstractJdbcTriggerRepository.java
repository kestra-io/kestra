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
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.QueueIndexerRepository;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.runner.JdbcTransactionContext;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcCrudRepository<Trigger> implements TriggerRepositoryInterface, QueueIndexerRepository<Trigger> {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");
    private static final Field<Object> NEXT_EXECUTION_DATE_FIELD = field("next_execution_date");
    private static final Field<Boolean> LOCKED_FIELD = field("locked", Boolean.class);
    private static final Field<Integer> VNODE_FIELD = field("vnode", Integer.class);

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
    public Optional<Trigger> findLast(TriggerId trigger) {
        return findOne(DSL.trueCondition(), field("key").eq(trigger.uid()));
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        return findOne(execution.getTenantId(), field("execution_id").eq(execution.getId()));
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
    public Trigger save(Trigger trigger) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, fields);

        return trigger;
    }

    @Override
    public Trigger save(TransactionContext txContext, Trigger trigger) {
        return save(txContext.unwrap(JdbcTransactionContext.class).getDslContext(), trigger);
    }

    @Override
    public <TX extends TransactionContext> boolean supports(Class<TX> clazz) {
        return JdbcTransactionContext.class.isAssignableFrom(clazz);
    }

    @Override
    public Class<Trigger> getItemClass() {
        return Trigger.class;
    }

    @Override
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

    @Override
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

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<Trigger> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL.using(configuration)
                .select(field("value"))
                .from(this.jdbcRepository.getTable())
                .where(NEXT_EXECUTION_DATE_FIELD.lessThan(now.toOffsetDateTime()).or(NEXT_EXECUTION_DATE_FIELD.isNull()))
                .and(LOCKED_FIELD.isNull().or(LOCKED_FIELD.eq(locked)))
                .and(VNODE_FIELD.in(vNodes))
                .orderBy(NEXT_EXECUTION_DATE_FIELD.asc())
                .fetch()
            )
            .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)));
    }

    @Override
    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);
}
