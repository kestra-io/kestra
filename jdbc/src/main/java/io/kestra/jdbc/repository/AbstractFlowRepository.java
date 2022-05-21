package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.ConstraintViolationException;

@Singleton
public abstract class AbstractFlowRepository extends AbstractRepository implements FlowRepositoryInterface {
    private final QueueInterface<Flow> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher;
    private final ModelValidator modelValidator;
    protected AbstractJdbcRepository<Flow> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractFlowRepository(AbstractJdbcRepository<Flow> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
        this.modelValidator = applicationContext.getBean(ModelValidator.class);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.flowQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record1<Object>> from;

                if (revision.isPresent()) {
                    from = context
                        .select(DSL.field("value"))
                        .from(jdbcRepository.getTable())
                        .where(DSL.field("namespace").eq(namespace))
                        .and(DSL.field("id").eq(id))
                        .and(DSL.field("revision").eq(revision.get()));
                } else {
                    from = context
                        .select(DSL.field("value"))
                        .from(this.lastRevision(true))
                        .where(this.defaultFilter())
                        .and(DSL.field("namespace").eq(namespace))
                        .and(DSL.field("id").eq(id));
                }

                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                Select<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(jdbcRepository.getTable())
                    .where(DSL.field("namespace").eq(namespace))
                    .and(DSL.field("id").eq(id))
                    .orderBy(DSL.field("revision").asc());

                return this.jdbcRepository.fetch(select);
            });
    }

    protected Table<Record> lastRevision(boolean asterisk) {
        List<SelectFieldOrAsterisk> fields = new ArrayList<>();
        if (asterisk) {
            fields.add(DSL.asterisk());
        } else {
            fields.add(DSL.field(DSL.quotedName("key")));
            fields.add(DSL.field("revision"));
        }

        fields.add(
            DSL.rowNumber()
                .over()
                .partitionBy(DSL.field("namespace"), DSL.field("id"))
                .orderBy(DSL.field("revision").desc())
                .as("revision_rows")
        );

        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                return context.select(DSL.asterisk())
                    .from(
                        context.select(fields)
                            .from(jdbcRepository.getTable())
                            .asTable("rev_ord")
                    )
                    .where(DSL.field("revision_rows").eq(1))
                    .asTable("rev");
            });
    }

    @Override
    public List<Flow> findAll() {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(lastRevision(true))
                    .where(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findAllWithRevisions() {
        return jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findByNamespace(String namespace) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(lastRevision(true))
                    .where(DSL.field("namespace").eq(namespace))
                    .and(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public Flow create(Flow flow) throws ConstraintViolationException {
        // control if create is valid
        flow.validate()
            .ifPresent(s -> {
                throw s;
            });

        return this.save(flow, CrudEventType.CREATE);
    }

    @Override
    public Flow update(Flow flow, Flow previous) throws ConstraintViolationException {
        // control if update is valid
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(flow))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        Flow saved = this.save(flow, CrudEventType.UPDATE);

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return saved;
    }

    @SneakyThrows
    private Flow save(Flow flow, CrudEventType crudEventType) throws ConstraintViolationException {
        // validate the flow
        modelValidator
            .isValid(flow)
            .ifPresent(s -> {
                throw s;
            });

        // flow exists, return it
        Optional<Flow> exists = this.findById(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow)) {
            return exists.get();
        }

        List<Flow> revisions = this.findRevisions(flow.getNamespace(), flow.getId());

        if (revisions.size() > 0) {
            flow = flow.withRevision(revisions.get(revisions.size() - 1).getRevision() + 1);
        } else {
            flow = flow.withRevision(1);
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flow);
        fields.put(DSL.field("source_code"), JacksonMapper.ofYaml().writeValueAsString(flow));

        this.jdbcRepository.persist(flow, fields);

        flowQueue.emit(flow);
        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return flow;
    }

    @SneakyThrows
    @Override
    public Flow delete(Flow flow) {
        Optional<Flow> revision = this.findById(flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision()));
        if (revision.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        Optional<Flow> last = this.findById(flow.getNamespace(), flow.getId());
        if (last.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        if (!last.get().getRevision().equals(revision.get().getRevision())) {
            throw new IllegalStateException("Trying to deleted old revision, wanted " + revision.get().getRevision() + ", last revision is " + last.get().getRevision());
        }

        Flow deleted = flow.toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        fields.put(DSL.field("source_code"), JacksonMapper.ofYaml().writeValueAsString(deleted));

        this.jdbcRepository.persist(deleted, fields);

        flowQueue.emit(deleted);

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace() {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(DSL.field("namespace"))
                .from(lastRevision(false))
                .where(this.defaultFilter())
                .groupBy(DSL.grouping(DSL.field("namespace")))
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }
}
