package io.kestra.jdbc.repository;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.kestra.jdbc.runner.JdbcQueueIndexerInterface;
import io.kestra.jdbc.runner.JdbcSchedulerContext;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import org.jooq.*;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcRepository implements TriggerRepositoryInterface, JdbcQueueIndexerInterface<Trigger> {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");

    protected io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository;

    public AbstractJdbcTriggerRepository(io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
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
                field("next_execution_date").lessThan(now.toOffsetDateTime())
                    // we check for null for backwards compatibility
                    .or(field("next_execution_date").isNull())
            )
            .orderBy(field("next_execution_date").asc())
            .forUpdate()
            .skipLocked()
            .fetch()
            .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)));
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
    public Flux<Trigger> find(String query, String tenantId, String namespace) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    DSLContext context = DSL.using(configuration);

                    var select = context
                        .select(
                            field("value")
                        )
                        .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                        .from(this.jdbcRepository.getTable())
                        .where(this.defaultFilter(tenantId));
                    if (namespace != null) {
                        select =  select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
                    }
                    if (query != null) {
                        select = select.and(this.fullTextCondition(query));
                    }

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
}
