package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.ScheduleContextInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import io.kestra.jdbc.runner.JdbcSchedulerContext;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcRepository implements TriggerRepositoryInterface, JdbcIndexerInterface<Trigger> {
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
            .fetch()
            .map(r -> this.jdbcRepository.deserialize(r.get("value").toString()));
    }

    @Override
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

    @Override
    public void delete(Trigger trigger) {
        this.jdbcRepository.delete(trigger);
    }

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

    @Override
    public ArrayListTotal<Trigger> find(Pageable pageable, String query, String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(field("value"))
                    .hint(context.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.fullTextCondition(query))
                    .and(this.defaultFilter(tenantId));

                if (namespace != null) {
                    select.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
                }

                select.and(this.defaultFilter());

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    protected Condition defaultFilter(String tenantId) {
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
            "executionId", "execution_id"
        );

        return s -> mapper.getOrDefault(s, s);
    }
}
