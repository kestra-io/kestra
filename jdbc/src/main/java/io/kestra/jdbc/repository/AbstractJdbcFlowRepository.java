package io.kestra.jdbc.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.jdbc.JdbcMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.*;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Slf4j
public abstract class AbstractJdbcFlowRepository extends AbstractJdbcRepository implements FlowRepositoryInterface {
    private static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);

    private final QueueInterface<FlowWithSource> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher;
    private final ModelValidator modelValidator;
    private final NamespaceUtils namespaceUtils;
    protected io.kestra.jdbc.AbstractJdbcRepository<Flow> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractJdbcFlowRepository(io.kestra.jdbc.AbstractJdbcRepository<Flow> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
        this.modelValidator = applicationContext.getBean(ModelValidator.class);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.flowQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));
        this.namespaceUtils = applicationContext.getBean(NamespaceUtils.class);
        this.jdbcRepository.setDeserializer(record -> {
            String source = record.get("value", String.class);

            try {
                Flow deserialize = this.jdbcRepository.deserialize(source);

                // raise exception for invalid flow, ex: Templates disabled
                deserialize.allTasksWithChilds();

                return deserialize;
            } catch (DeserializationException e) {
                try {
                    JsonNode jsonNode = JdbcMapper.of().readTree(source);
                    return FlowWithException.from(jsonNode, e).orElseThrow(() -> e);
                } catch (JsonProcessingException ex) {
                    throw new DeserializationException(ex, source);
                }
            }
        });
    }

    @Override
    public Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record1<String>> from;

                if (revision.isPresent()) {
                    from = context
                        .select(field("value", String.class))
                        .from(jdbcRepository.getTable())
                        .where(this.revisionDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(revision.get()));
                } else {
                    from = context
                        .select(field("value", String.class))
                        .from(fromLastRevision(true))
                        .where(allowDeleted ? this.revisionDefaultFilter(tenantId) : this.defaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id));
                }

                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public Optional<Flow> findByIdWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record1<String>> from;

                if (revision.isPresent()) {
                    from = context
                        .select(field("value", String.class))
                        .from(jdbcRepository.getTable())
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(revision.get()));
                } else {
                    from = context
                        .select(field("value", String.class))
                        .from(fromLastRevision(true))
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id));
                }


                return this.jdbcRepository.fetchOne(from);
            });
    }

    protected Table<Record> fromLastRevision(boolean asterisk) {
        return JdbcFlowRepositoryService.lastRevision(jdbcRepository, asterisk);
    }

    protected Condition revisionDefaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    protected Condition noAclDefaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    protected Condition defaultExecutionFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
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
                        .where(this.revisionDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer)))
                    .orElseGet(() -> context
                        .select(
                            field("source_code", String.class),
                            field("value", String.class)
                        )
                        .from(fromLastRevision(true))
                        .where(allowDeleted ? this.revisionDefaultFilter(tenantId) : this.defaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id)));
                Record2<String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                Flow flow = jdbcRepository.map(fetched);
                String source = fetched.get("source_code", String.class);
                if (flow instanceof FlowWithException fwe) {
                    return Optional.of(fwe.toBuilder().source(source).build());
                }
                return Optional.of(FlowWithSource.of(flow, source));
            });
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSourceWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision) {
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
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(field("namespace").eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer)))
                    .orElseGet(() -> context
                        .select(
                            field("source_code", String.class),
                            field("value", String.class)
                        )
                        .from(fromLastRevision(true))
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(field("namespace", String.class).eq(namespace))
                        .and(field("id", String.class).eq(id)));
                Record2<String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                Flow flow = jdbcRepository.map(fetched);
                String source = fetched.get("source_code", String.class);
                if (flow instanceof FlowWithException fwe) {
                    return Optional.of(fwe.toBuilder().source(source).build());
                }
                return Optional.of(FlowWithSource.of(flow, source));
            });
    }

    @Override
    public List<FlowWithSource> findRevisions(String tenantId, String namespace, String id) {
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
                    .where(this.revisionDefaultFilter(tenantId))
                    .and(NAMESPACE_FIELD.eq(namespace))
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
    public int count(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .selectCount()
                .from(fromLastRevision(true))
                .where(this.defaultFilter(tenantId))
                .fetchOne(0, int.class));
    }

    @Override
    public int countForNamespace(String tenantId, @Nullable String namespace) {
        if (namespace == null) return count(tenantId);

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .selectCount()
                .from(fromLastRevision(true))
                .where(this.defaultFilter(tenantId))
                .and(NAMESPACE_FIELD.eq(namespace))
                .fetchOne(0, int.class));
    }

    @Override
    public List<Flow> findAll(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                List<Flow> flows = new ArrayList<>();
                select.fetch().forEach(
                    item -> {
                        try {
                            Flow flow = this.jdbcRepository.map(item);
                            flows.add(flow);
                        } catch (Exception e) {
                            log.error("Unable to load the following flow:\n{}", item.get("value", String.class), e);
                        }
                    }
                );
                return flows;
            });
    }

    @Override
    public List<FlowWithSource> findAllWithSource(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"), field("source_code", String.class))
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    jdbcRepository.map(record),
                    record.get("source_code", String.class)
                ));
            });
    }

    @Override
    public List<FlowWithSource> findAllWithSourceForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"), field("source_code", String.class))
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllWithSourceForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                return select.fetch().map(record -> {
                    try {
                        return FlowWithSource.of(
                            jdbcRepository.map(record),
                            record.get("source_code", String.class)
                        );
                    } catch (Exception e) {
                        log.error("Unable to load the following flow:\n{}", record.get("value", String.class), e);
                        return null;
                    }
                });
            });
    }

    @Override
    public List<Flow> findByNamespace(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    findByNamespaceSelect(namespace)
                    .and(this.defaultFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    findByNamespacePrefixSelect(namespacePrefix)
                        .and(this.defaultFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    findByNamespaceSelect(namespace)
                    .and(this.defaultExecutionFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            }).stream().map(FlowForExecution::of).toList();
    }

    private SelectConditionStep<Record1<Object>> findByNamespaceSelect(String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("value"))
                .from(fromLastRevision(true))
                .where(NAMESPACE_FIELD.eq(namespace)));
    }

    private SelectConditionStep<Record1<Object>> findByNamespacePrefixSelect(String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("value"))
                .from(fromLastRevision(true))
                .where(DSL.or(NAMESPACE_FIELD.eq(namespacePrefix), NAMESPACE_FIELD.likeIgnoreCase(namespacePrefix + ".%"))));
    }

    @Override
    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record2<String, String>> select = DSL
                    .using(configuration)
                    .select(
                        field("source_code", String.class),
                        field("value", String.class)
                    )
                    .from(fromLastRevision(true))
                    .where(NAMESPACE_FIELD.eq(namespace))
                    .and(this.defaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    jdbcRepository.map(record),
                    record.get("source_code", String.class)
                ));
            });
    }

    @SuppressWarnings("unchecked")
    private <R extends Record, E> SelectConditionStep<R> fullTextSelect(String tenantId, DSLContext context, List<Field<Object>> field) {
        ArrayList<Field<Object>> fields = new ArrayList<>(Collections.singletonList(field("value")));

        if (field != null) {
            fields.addAll(field);
        }

        return (SelectConditionStep<R>) context
            .select(fields)
            .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
            .from(fromLastRevision(false))
            .join(jdbcRepository.getTable().as("ft"))
            .on(
                DSL.field(DSL.quotedName("ft", "key")).eq(DSL.field(DSL.field(DSL.quotedName("rev", "key"))))
                    .and(DSL.field(DSL.quotedName("ft", "revision")).eq(DSL.field(DSL.quotedName("rev", "revision"))))
            )
            .where(this.defaultFilter(tenantId));
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.fullTextSelect(tenantId, context, Collections.emptyList());

                select.and(this.findCondition(query, labels));

                if (scope != null && !scope.containsAll(Arrays.stream(FlowScope.values()).toList())) {
                    if (scope.contains(FlowScope.USER)) {
                        select = select.and(field("namespace").ne(namespaceUtils.getSystemFlowNamespace()));
                    } else if (scope.contains(FlowScope.SYSTEM)) {
                        select = select.and(field("namespace").eq(namespaceUtils.getSystemFlowNamespace()));
                    }
                }

                if (namespace != null) {
                    select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public List<FlowWithSource> findWithSource(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                List<Field<Object>> fields = List.of(field("value"), field("source_code"));
                SelectConditionStep<Record> select = this.fullTextSelect(tenantId, context, fields);

                select.and(this.findCondition(query, labels));

                if (scope != null && !new HashSet<>(scope).containsAll(Arrays.stream(FlowScope.values()).toList())) {
                    if (scope.contains(FlowScope.USER)) {
                        select = select.and(field("namespace").ne(namespaceUtils.getSystemFlowNamespace()));
                    } else if (scope.contains(FlowScope.SYSTEM)) {
                        select = select.and(field("namespace").eq(namespaceUtils.getSystemFlowNamespace()));
                    }
                }

                if (namespace != null) {
                    select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
                }

                return select.fetch().map(record -> FlowWithSource.of(
                    jdbcRepository.map(record),
                    record.get("source_code", String.class)
                ));
            });
    }


    abstract protected Condition findSourceCodeCondition(String query);

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String tenantId, @Nullable String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record> select = this.fullTextSelect(tenantId, context, Collections.singletonList(field("source_code")));

                if (query != null) {
                    select.and(this.findSourceCodeCondition(query));
                }

                if (namespace != null) {
                    select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.likeIgnoreCase(namespace + ".%")));
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
        if (this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()) {
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

    @SneakyThrows(QueueException.class)
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
            .forEach(throwConsumer(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger))));

        return this.save(flow, CrudEventType.UPDATE, flowSource);
    }

    @SneakyThrows
    private FlowWithSource save(Flow flow, CrudEventType crudEventType, String flowSource) throws ConstraintViolationException {
        if (flow instanceof FlowWithSource flowWithSource) {
            flow = flowWithSource.toFlow();
        }

        // flow exists, return it
        Optional<FlowWithSource> exists = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equals(flow, flowSource)) {
            return exists.get();
        }

        List<FlowWithSource> revisions = this.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId());

        if (!revisions.isEmpty()) {
            flow = flow.toBuilder().revision(revisions.get(revisions.size() - 1).getRevision() + 1).build();
        } else {
            flow = flow.toBuilder().revision(1).build();
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flow);
        fields.put(field("source_code"), flowSource);

        this.jdbcRepository.persist(flow, fields);

        flowQueue.emit(flow.withSource(flowSource));
        if (exists.isPresent()) {
            eventPublisher.publishEvent(new CrudEvent<>(flow, exists.get(), crudEventType));
        } else {
            eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));
        }

        return FlowWithSource.of(flow, flowSource);
    }

    @SneakyThrows
    @Override
    public FlowWithSource delete(FlowWithSource flow) {
        Optional<FlowWithSource> revision = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision()));
        if (revision.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        Optional<FlowWithSource> last = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId());
        if (last.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        if (!last.get().getRevision().equals(revision.get().getRevision())) {
            throw new IllegalStateException("Trying to deleted old revision, wanted " + revision.get().getRevision() + ", last revision is " + last.get().getRevision());
        }

        FlowWithSource deleted = flow.toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        fields.put(field("source_code"), deleted.getSource());

        this.jdbcRepository.persist(deleted, fields);

        flowQueue.emit(deleted);

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(NAMESPACE_FIELD)
                .from(fromLastRevision(true))
                .where(this.defaultFilter(tenantId))
                .groupBy(NAMESPACE_FIELD)
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }

    @Override
    public List<String> findDistinctNamespaceExecutable(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(NAMESPACE_FIELD)
                .from(fromLastRevision(true))
                .where(this.defaultExecutionFilter(tenantId))
                .groupBy(NAMESPACE_FIELD)
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }

    @Override
    public Integer lastRevision(String tenantId, String namespace, String id) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .fetchValue(
                    DSL.select(field("revision", Integer.class))
                        .from(fromLastRevision(true))
                        .where(this.defaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .limit(1)
                )
            );
    }
}
