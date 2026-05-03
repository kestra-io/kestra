package io.kestra.jdbc.repository;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.ITriggers;
import io.kestra.plugin.core.dashboard.data.Triggers;

import io.micronaut.data.model.Pageable;
import lombok.Getter;
import reactor.core.publisher.Flux;

public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcCrudRepository<TriggerState> implements TriggerRepositoryInterface, TriggerStateStore {

    private static final Field<Object> NAMESPACE_FIELD = field("namespace");
    private static final Field<Long> NEXT_EVALUATION_EPOCH_FIELD = field("next_evaluation_epoch", Long.class);
    private static final Field<Boolean> LOCKED_FIELD = field("locked", Boolean.class);
    private static final Field<Integer> VNODE_FIELD = field("vnode", Integer.class);
    private static final Field<Object> FLOW_ID_FIELD = field("flow_id");
    private static final Field<Object> WORKER_ID_FIELD = field("worker_id");
    private static final Field<Object> VALUE_FIELD = field("value");
    private static final String NEXT_EVALUATION_DATE_COLUMN = "next_evaluation_date";
    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private final JdbcFilterService filterService;

    @Getter
    private final Map<Triggers.Fields, String> fieldsMapping = Map.of(
        Triggers.Fields.ID, KEY_FIELD.getName(),
        Triggers.Fields.NAMESPACE, NAMESPACE_FIELD.getName(),
        Triggers.Fields.FLOW_ID, FLOW_ID_FIELD.getName(),
        Triggers.Fields.TRIGGER_ID, "trigger_id",
        Triggers.Fields.EXECUTION_ID, "execution_id",
        Triggers.Fields.NEXT_EXECUTION_DATE, NEXT_EVALUATION_DATE_COLUMN,
        Triggers.Fields.WORKER_ID, WORKER_ID_FIELD.getName()
    );

    @Override
    public Set<Triggers.Fields> dateFields() {
        return Set.of(Triggers.Fields.NEXT_EXECUTION_DATE);
    }

    @Override
    public Triggers.Fields dateFilterField() {
        return Triggers.Fields.NEXT_EXECUTION_DATE;
    }

    public AbstractJdbcTriggerRepository(io.kestra.jdbc.AbstractJdbcRepository<TriggerState> jdbcRepository,
        JdbcFilterService filterService) {
        super(jdbcRepository);
        this.filterService = filterService;
    }

    @Override
    public Optional<TriggerState> findById(TriggerId trigger) {
        return findOne(DSL.noCondition(), KEY_FIELD.eq(trigger.uid()));
    }

    @Override
    public List<TriggerState> findAll(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<TriggerState> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public TriggerState create(TriggerState trigger) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSL.using(configuration)
                    .insertInto(this.jdbcRepository.getTable())
                    .set(KEY_FIELD, trigger.uid())
                    .set(this.jdbcRepository.persistFields(trigger))
                    .execute();

                return trigger;
            });
    }

    @Override
    public void delete(TriggerId trigger) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                DSL.using(configuration)
                    .delete(this.jdbcRepository.getTable())
                    .where(KEY_FIELD.eq(trigger.uid()))
                    .execute();
            });
    }

    @Override
    public ArrayListTotal<TriggerState> find(Pageable pageable, String tenantId, List<QueryFilter> filters) {
        var condition = filter(filters, NEXT_EVALUATION_DATE_COLUMN, Resource.TRIGGER);
        return findPage(pageable, tenantId, condition);
    }

    @Override
    public ArrayListTotal<TriggerState> find(Pageable pageable, String query, String tenantId, String namespace, String flowId, String workerId) {
        var condition = this.fullTextCondition(query).and(this.defaultFilter());

        if (namespace != null) {
            condition = condition.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.startsWith(namespace + ".")));
        }

        if (flowId != null) {
            condition = condition.and(FLOW_ID_FIELD.eq(flowId));
        }

        if (workerId != null) {
            condition = condition.and(WORKER_ID_FIELD.eq(workerId));
        }

        return findPage(pageable, tenantId, condition);
    }

    @Override
    public Flux<TriggerState> find(String tenantId, List<QueryFilter> filters) {
        var condition = filter(filters, NEXT_EVALUATION_DATE_COLUMN, Resource.TRIGGER);
        return findAsync(tenantId, condition);
    }

    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.noCondition() : jdbcRepository.fullTextCondition(List.of("fulltext"), query);
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
        return DSL.noCondition();
    }

    @Override
    public Function<String, String> sortMapping() throws IllegalArgumentException {
        Map<String, String> mapper = Map.of(
            "flowId", "flow_id",
            "triggerId", "trigger_id",
            "executionId", "execution_id",
            "nextExecutionDate", NEXT_EVALUATION_DATE_COLUMN
        );

        return s -> mapper.getOrDefault(s, s);
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Triggers.Fields, ? extends ColumnDescriptor<Triggers.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
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
    public Double fetchValue(String tenantId, DataFilterKPI<ITriggers.Fields, ? extends ColumnDescriptor<ITriggers.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration ->
        {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<ITriggers.Fields> columnDescriptor = dataFilter.getColumns();
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
    public List<TriggerState> findAllForVNodes(Set<Integer> vNodes) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL.using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(VNODE_FIELD.in(vNodes))
                    .fetch()
            )
            .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) {
        final long epochMilli = now.toInstant().toEpochMilli();
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL.using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(NEXT_EVALUATION_EPOCH_FIELD.le(epochMilli).or(NEXT_EVALUATION_EPOCH_FIELD.isNull()))
                    .and(LOCKED_FIELD.isNull().or(LOCKED_FIELD.eq(locked)))
                    .and(VNODE_FIELD.in(vNodes))
                    .orderBy(NEXT_EVALUATION_EPOCH_FIELD.asc())
                    .fetch()
            )
            .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)));
    }

    @Override
    abstract protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType);

    @SuppressWarnings("removal")
    @Override
    public List<Trigger> findAllForAllTenantsV1() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable());

                return select.fetch().map(record ->
                {
                    String json = record.get("value", String.class);
                    try {
                        return JdbcMapper.of().readValue(json, Trigger.class);
                    } catch (IOException e) {
                        throw new DeserializationException(e, json);
                    }
                });
            });
    }
}
