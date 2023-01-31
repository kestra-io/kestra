package io.kestra.jdbc.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
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
                    return FlowWithException.builder()
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
    public Optional<FlowWithSource> findByIdWithSource(String namespace, String id, Optional<Integer> revision) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record2<String, String>> from;

                from = revision.map(integer -> context
                        .select(
                            field("source_code", String.class),
                            field("value", String.class)
                        )
                        .from(jdbcRepository.getTable())
                        .where(field("namespace").eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer)))
                    .orElseGet(() -> context
                        .select(
                            field("source_code", String.class),
                            field("value", String.class)
                        )
                        .from(JdbcFlowRepositoryService.lastRevision(jdbcRepository, true))
                        .where(this.defaultFilter())
                        .and(field("namespace", String.class).eq(namespace))
                        .and(field("id", String.class).eq(id)));
                Record2<String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                 return Optional.of(FlowWithSource.of(
                    jdbcRepository.map(fetched),
                    fetched.get("source_code", String.class)
                ));
            });
    }

    @Override
    public List<FlowWithSource> findRevisions(String namespace, String id) {
         return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record2<String, String>> select = DSL
                    .using(configuration)
                    .select(
                        field("source_code", String.class),
                        field("value", String.class)
                    )
                    .from(jdbcRepository.getTable())
                    .where(field("namespace", String.class).eq(namespace))
                    .and(field("id", String.class).eq(id))
                    .orderBy(field("revision", Integer.class).asc());

                return select
                    .fetch()
                    .map(record -> FlowWithSource.of(
                        jdbcRepository.map(record),
                        record.get("source_code", String.class)
                    ));
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
    public FlowWithSource create(Flow flow, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException {
        if (this.findById(flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow id already exists",
                flow,
                Flow.class,
                "flow.id",
                flow.getId()
            )));
        }

        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        return this.save(flow, CrudEventType.CREATE, flowSource);
    }

    @Override
    public FlowWithSource update(Flow flow, Flow previous, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException {
        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        // control if update is valid
        Optional<ConstraintViolationException> checkUpdate = previous.validateUpdate(flowWithDefaults);
        if(checkUpdate.isPresent()){
            throw checkUpdate.get();
        }

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return this.save(flow, CrudEventType.UPDATE, flowSource);
    }

    @SneakyThrows
    private FlowWithSource save(Flow flow, CrudEventType crudEventType, String flowSource) throws ConstraintViolationException {
        // flow exists, return it
        Optional<FlowWithSource> exists = this.findByIdWithSource(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().isUpdatable(flow, flowSource)) {
            return exists.get();
        }

        List<FlowWithSource> revisions = this.findRevisions(flow.getNamespace(), flow.getId());

        if (revisions.size() > 0) {
            flow = flow.withRevision(revisions.get(revisions.size() - 1).getRevision() + 1);
        } else {
            flow = flow.withRevision(1);
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flow);
        fields.put(field("source_code"), flowSource);

        this.jdbcRepository.persist(flow, fields);

        flowQueue.emit(flow);
        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return FlowWithSource.of(flow, flowSource);
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
