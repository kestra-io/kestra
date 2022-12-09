package io.kestra.jdbc.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.jdbc.JdbcMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.*;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;

@Singleton
public abstract class AbstractJdbcFlowRepository extends AbstractJdbcRepository implements FlowRepositoryInterface {
    private final QueueInterface<Flow> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher;
    private final ModelValidator modelValidator;
    protected io.kestra.jdbc.AbstractJdbcRepository<Flow> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractJdbcFlowRepository(io.kestra.jdbc.AbstractJdbcRepository<Flow> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
        this.modelValidator = applicationContext.getBean(ModelValidator.class);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.flowQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));

        this.jdbcRepository.setDeserializer(record -> {
            String source = record.get("value", String.class);

            try {
                return this.jdbcRepository.deserialize(source);
            } catch (DeserializationException e) {
                try {
                    JsonNode jsonNode = JdbcMapper.of().readTree(source);
                    return FlowSource.builder()
                        .id(jsonNode.get("id").asText())
                        .namespace(jsonNode.get("namespace").asText())
                        .revision(jsonNode.get("revision").asInt())
                        .source(JacksonMapper.ofJson().writeValueAsString(JacksonMapper.toMap(source)))
                        .exception(e.getMessage())
                        .tasks(List.of())
                        .build();
                } catch (JsonProcessingException ex) {
                    throw new DeserializationException(ex);
                }
            }
        });
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record1<String>> from;

                if (revision.isPresent()) {
                    from = context
                        .select(field("value", String.class))
                        .from(jdbcRepository.getTable())
                        .where(field("namespace").eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(revision.get()));
                } else {
                    from = context
                        .select(field("value", String.class))
                        .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, true))
                        .where(this.defaultFilter())
                        .and(field("namespace", String.class).eq(namespace))
                        .and(field("id", String.class).eq(id));
                }

                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<String>> select = DSL
                    .using(configuration)
                    .select(field("value", String.class))
                    .from(jdbcRepository.getTable())
                    .where(field("namespace", String.class).eq(namespace))
                    .and(field("id", String.class).eq(id))
                    .orderBy(field("revision", Integer.class).asc());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, true))
                    .where(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findAllWithRevisions() {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findByNamespace(String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, true))
                    .where(field("namespace").eq(namespace))
                    .and(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    @SuppressWarnings("unchecked")
    private <R extends Record, E> SelectConditionStep<R> fullTextSelect(DSLContext context, List<Field<Object>> field) {
        ArrayList<Field<Object>> fields = new ArrayList<>(Collections.singletonList(field("value")));

        if (field != null) {
            fields.addAll(field);
        }

        return (SelectConditionStep<R>) context
            .select(fields)
            .hint(context.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
            .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, false))
            .join(jdbcRepository.getTable().as("ft"))
            .on(
                DSL.field(DSL.quotedName("ft", "key")).eq(DSL.field(DSL.field(DSL.quotedName("rev", "key"))))
                    .and(DSL.field(DSL.quotedName("ft", "revision")).eq(DSL.field(DSL.quotedName("rev", "revision"))))
            )
            .where(this.defaultFilter());
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.fullTextSelect(context, Collections.emptyList());

                select.and(this.findCondition(query, labels));

                if (namespace != null) {
                    select.and(field("namespace").likeIgnoreCase(namespace + "%"));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    abstract protected Condition findSourceCodeCondition(String query);

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record> select = this.fullTextSelect(context, Collections.singletonList(field("source_code")));

                if (query != null) {
                    select.and(this.findSourceCodeCondition(query));
                }

                if (namespace != null) {
                    select.and(field("namespace").likeIgnoreCase(namespace + "%"));
                }

                return this.jdbcRepository.fetchPage(
                    context,
                    select,
                    pageable,
                    record -> new SearchResult<>(
                        this.jdbcRepository.map(record),
                        this.jdbcRepository.fragments(query, record.getValue("source_code", String.class))
                    )
                );
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
        fields.put(field("source_code"), JacksonMapper.ofYaml().writeValueAsString(flow));

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
        fields.put(field("source_code"), JacksonMapper.ofYaml().writeValueAsString(deleted));

        this.jdbcRepository.persist(deleted, fields);

        flowQueue.emit(deleted);

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("namespace"))
                .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, true))
                .where(this.defaultFilter())
                .groupBy(field("namespace"))
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }
}
