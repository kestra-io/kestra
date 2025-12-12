package io.kestra.jdbc.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
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
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Flows;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Slf4j
public abstract class AbstractJdbcFlowRepository extends AbstractJdbcRepository implements FlowRepositoryInterface {

    protected static final ObjectMapper MAPPER = JdbcMapper.of();

    private static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);
    public static final Field<String> VALUE_FIELD = field("value", String.class);
    public static final Field<String> TENANT_FIELD = field("tenant_id", String.class);
    public static final Field<String> SOURCE_FIELD = field("source_code", String.class);

    private final QueueInterface<FlowInterface> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher;
    private final ModelValidator modelValidator;
    private final NamespaceUtils namespaceUtils;
    private final PluginDefaultService pluginDefaultService;

    private final JdbcFilterService filterService;

    protected io.kestra.jdbc.AbstractJdbcRepository<FlowInterface> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractJdbcFlowRepository(
        io.kestra.jdbc.AbstractJdbcRepository<FlowInterface> jdbcRepository,
        ApplicationContext applicationContext,
        JdbcFilterService filterService
    ) {
        this.jdbcRepository = jdbcRepository;
        this.modelValidator = applicationContext.getBean(ModelValidator.class);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.pluginDefaultService = applicationContext.getBean(PluginDefaultService.class);
        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.flowQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));
        this.namespaceUtils = applicationContext.getBean(NamespaceUtils.class);
        this.jdbcRepository.setDeserializer(record -> {
            String source = record.get("value", String.class);
            String namespace = record.get("namespace", String.class);
            String tenantId = record.get("tenant_id", String.class);
            try {
                Map<String, Object> map = MAPPER.readValue(source, new TypeReference<>() {
                });

                // Inject default plugin 'version' props before converting
                // to flow to correctly resolve to plugin type.
                map = pluginDefaultService.injectVersionDefaults(tenantId, namespace, map);

                Flow deserialize = MAPPER.convertValue(map, Flow.class);

                // raise exception for invalid flow, ex: Templates disabled
                deserialize.allTasksWithChilds();

                return deserialize;
            } catch (DeserializationException | IOException | IllegalArgumentException | FlowProcessingException e) {
                try {
                    JsonNode jsonNode = JdbcMapper.of().readTree(source);
                    return FlowWithException.from(jsonNode, e)
                        .orElseThrow(() -> e instanceof DeserializationException de ? de : new DeserializationException(e, source));
                } catch (JsonProcessingException ex) {
                    throw new DeserializationException(ex, source);
                }
            }
        });
        this.filterService = filterService;
    }

    @Getter
    private final Map<Flows.Fields, String> fieldsMapping = Map.of(
        Flows.Fields.ID, "key",
        Flows.Fields.NAMESPACE, "namespace",
        Flows.Fields.REVISION, "revision"
    );

    @Override
    public Set<Flows.Fields> dateFields() {
        return Set.of();
    }

    @Override
    public Flows.Fields dateFilterField() {
        return null;
    }

    @Override
    public Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record3<String, String, String>> from;

                from = revision.map(integer -> context
                    .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.revisionDefaultFilter(tenantId))
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id))
                    .and(field("revision", Integer.class).eq(integer)
                    )
                ).orElseGet(() -> context
                    .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                    .from(fromLastRevision(true))
                    .where(allowDeleted ? this.revisionDefaultFilter(tenantId) : this.defaultFilter(tenantId))
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id))
                );

                return this.jdbcRepository.fetchOne(from).map(it -> (Flow) it);
            });
    }

    @Override
    public Optional<Flow> findByIdWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record3<String, String, String>> from;

                from = revision
                    .map(integer -> context
                        .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                        .from(jdbcRepository.getTable())
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer))
                    ).orElseGet(() -> context
                        .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                        .from(fromLastRevision(true))
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                    );

                return this.jdbcRepository.fetchOne(from).map(it -> (Flow) it);
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
                Select<Record4<String, String, String, String>> from;

                from = revision.map(integer -> context
                        .select(
                            SOURCE_FIELD,
                            VALUE_FIELD,
                            NAMESPACE_FIELD,
                            TENANT_FIELD
                        )
                        .from(jdbcRepository.getTable())
                        .where(this.revisionDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer)))
                    .orElseGet(() -> context
                        .select(
                            SOURCE_FIELD,
                            VALUE_FIELD,
                            NAMESPACE_FIELD,
                            TENANT_FIELD
                        )
                        .from(fromLastRevision(true))
                        .where(allowDeleted ? this.revisionDefaultFilter(tenantId) : this.defaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id)));

                Record4<String, String, String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                Flow flow = (Flow) jdbcRepository.map(fetched);
                String source = fetched.get(SOURCE_FIELD);
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
                Select<Record4<String, String, String, String>> from;

                from = revision.map(integer -> context
                        .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                        .from(jdbcRepository.getTable())
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(field("revision", Integer.class).eq(integer)))
                    .orElseGet(() -> context
                        .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                        .from(fromLastRevision(true))
                        .where(this.noAclDefaultFilter(tenantId))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id)));
                Record4<String, String, String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                Flow flow = (Flow) jdbcRepository.map(fetched);
                String source = fetched.get(SOURCE_FIELD);
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
                Select<Record4<String, String, String, String>> select = DSL
                    .using(configuration)
                    .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.revisionDefaultFilter(tenantId))
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id))
                    .orderBy(field("revision", Integer.class).asc());

                return select.fetch()
                    .map(record -> FlowWithSource.of((Flow) jdbcRepository.map(record), record.get(SOURCE_FIELD)));
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Flow> findAll(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record3<Object, Object, Object>> select = DSL
                    .using(configuration)
                    .select(
                        field("value"),
                        field("namespace"),
                        field("tenant_id")
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId));

                return (List) this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Flow> findAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(
                        field("value"),
                        field("namespace"),
                        field("tenant_id")
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                List<Flow> flows = new ArrayList<>();
                select.fetch().forEach(
                    item -> {
                        try {
                            Flow flow = (Flow) this.jdbcRepository.map(item);
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
                    .select(
                        field("value"),
                        field("source_code"),
                        field("namespace"),
                        field("tenant_id")
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    (Flow) jdbcRepository.map(record),
                    record.get(SOURCE_FIELD)
                ));
            });
    }

    @Override
    public List<FlowWithSource> findAllWithSourceWithNoAcl(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(
                        field("value"),
                        field("source_code"),
                        field("namespace"),
                        field("tenant_id")
                    )
                    .from(fromLastRevision(true))
                    .where(this.noAclDefaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    (Flow) jdbcRepository.map(record),
                    record.get(SOURCE_FIELD)
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
                    .select(
                        field("value"),
                        field("source_code"),
                        field("namespace"),
                        field("tenant_id")
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllWithSourceForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                return select.fetch().stream().map(record -> {
                    try {
                        return FlowWithSource.of((Flow) jdbcRepository.map(record), record.get("source_code", String.class));
                    } catch (Exception e) {
                        log.error("Unable to load the following flow:\n{}", record.get("value", String.class), e);
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
            });
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Flow> findByNamespace(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record3<Object, Object, Object>> select =
                    findByNamespaceSelect(namespace)
                        .and(this.defaultFilter(tenantId));

                return (List) this.jdbcRepository.fetch(select);
            });
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record3<Object, Object, Object>> select =
                    findByNamespacePrefixSelect(namespacePrefix)
                        .and(this.defaultFilter(tenantId));

                return (List) this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record3<Object, Object, Object>> select =
                    findByNamespaceSelect(namespace)
                        .and(this.defaultExecutionFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            }).stream().map(it -> (Flow) it).map(FlowForExecution::of).toList();
    }

    private SelectConditionStep<Record3<Object, Object, Object>> findByNamespaceSelect(String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("value"), field("namespace"), field("tenant_id"))
                .from(fromLastRevision(true))
                .where(NAMESPACE_FIELD.eq(namespace)));
    }

    private SelectConditionStep<Record3<Object, Object, Object>> findByNamespacePrefixSelect(String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("value"), field("namespace"), field("tenant_id"))
                .from(fromLastRevision(true))
                .where(DSL.or(NAMESPACE_FIELD.eq(namespacePrefix), NAMESPACE_FIELD.likeIgnoreCase(namespacePrefix + ".%"))));
    }

    @Override
    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record4<String, String, String, String>> select = DSL
                    .using(configuration)
                    .select(
                        SOURCE_FIELD,
                        VALUE_FIELD,
                        NAMESPACE_FIELD,
                        TENANT_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(NAMESPACE_FIELD.eq(namespace))
                    .and(this.defaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    (Flow) jdbcRepository.map(record),
                    record.get(SOURCE_FIELD)
                ));
            });
    }

    @Override
    public List<FlowWithSource> findByNamespacePrefixWithSource(String tenantId, String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record4<String, String, String, String>> select = DSL
                    .using(configuration)
                    .select(
                        SOURCE_FIELD,
                        VALUE_FIELD,
                        NAMESPACE_FIELD,
                        TENANT_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(DSL.or(NAMESPACE_FIELD.eq(namespacePrefix), NAMESPACE_FIELD.likeIgnoreCase(namespacePrefix + ".%")))
                    .and(this.defaultFilter(tenantId));

                return select.fetch().map(record -> FlowWithSource.of(
                    (Flow) jdbcRepository.map(record),
                    record.get(SOURCE_FIELD)
                ));
            });
    }

    @SuppressWarnings("unchecked")
    private <R extends Record, E> SelectConditionStep<R> fullTextSelect(String tenantId, DSLContext context, List<Field<Object>> field) {
        ArrayList<Field<Object>> fields = new ArrayList<>();
        // add mandatory fields
        fields.add(field("value"));
        fields.add(field("tenant_id"));
        fields.add(field("namespace"));

        if (field != null) {
            fields.addAll(field);
        }

        return (SelectConditionStep<R>) context
            .select(fields)
            .from(fromLastRevision(false))
            .join(jdbcRepository.getTable().as("ft"))
            .on(
                DSL.field(DSL.quotedName("ft", "key")).eq(DSL.field(DSL.field(DSL.quotedName("rev", "key"))))
                    .and(DSL.field(DSL.quotedName("ft", "revision")).eq(DSL.field(DSL.quotedName("rev", "revision"))))
            )
            .where(this.defaultFilter(tenantId));
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    protected Condition findQueryCondition(String query) {
        return findCondition(query, Map.of());
    }

    abstract protected Condition findCondition(Object value, QueryFilter.Op operation);

    @Override
    protected Condition findLabelCondition(Either<Map<?, ?>, String> value, QueryFilter.Op operation) {
        return findCondition(value.getLeft(), operation);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ArrayListTotal<Flow> find(Pageable pageable, @Nullable String tenantId, @Nullable List<QueryFilter> filters) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = getFindFlowSelect(tenantId, filters, context, null);

                return (ArrayListTotal) this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ArrayListTotal<FlowWithSource> findWithSource(Pageable pageable, @Nullable String tenantId, @Nullable List<QueryFilter> filters) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                SelectConditionStep<Record> select = getFindFlowSelect(tenantId, filters, context, List.of(field("source_code")));

                return (ArrayListTotal) this.jdbcRepository.fetchPage(
                    context,
                    select,
                    pageable,
                    record -> FlowWithSource.of(
                        (Flow) jdbcRepository.map(record),
                        record.get("source_code", String.class)
                    )
                );
            });
    }

    @SuppressWarnings("unchecked")
    private <R extends Record> SelectConditionStep<R> getFindFlowSelect(String tenantId, List<QueryFilter> filters, DSLContext context, List<Field<Object>> additionalFieldsToSelect) {
        var select = this.fullTextSelect(tenantId, context, additionalFieldsToSelect != null ? additionalFieldsToSelect : List.of());
        select = select.and(this.filter(filters, null, Resource.FLOW));
        return (SelectConditionStep<R>) select;
    }

    protected Name getColumnName(QueryFilter.Field field){
        if (QueryFilter.Field.FLOW_ID.equals(field)) {
            return DSL.quotedName("id");
        } else {
            return DSL.quotedName(field.name().toLowerCase());
        }
    }

    abstract protected Condition findSourceCodeCondition(String query);

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
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

                return (ArrayListTotal) this.jdbcRepository.fetchPage(
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
    public FlowWithSource create(GenericFlow flow) throws ConstraintViolationException {
        if (this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow id already exists",
                flow,
                GenericFlow.class,
                "flow.id",
                flow.getId()
            )));
        }
        return this.save(flow, CrudEventType.CREATE);
    }

    @SneakyThrows({QueueException.class, FlowProcessingException.class})
    @Override
    public FlowWithSource update(GenericFlow flow, FlowInterface previous) throws ConstraintViolationException {
        // Check Flow with defaults
        FlowWithSource flowWithDefault = pluginDefaultService.injectAllDefaults(flow, false);
        modelValidator.validate(flowWithDefault);

        Flow previousFlow;
        if (previous instanceof Flow o) {
            previousFlow = o;
        } else {
            previousFlow = pluginDefaultService.injectAllDefaults(previous, false);
        }

        // Check update
        Optional<ConstraintViolationException> checkUpdate = previousFlow.validateUpdate(flowWithDefault);
        if (checkUpdate.isPresent()) {
            throw checkUpdate.get();
        }

        // Delete removed triggers
        FlowService
            .findRemovedTrigger(flowWithDefault, previousFlow)
            .forEach(throwConsumer(abstractTrigger -> triggerQueue.delete(Trigger.of(flowWithDefault, abstractTrigger))));

        // Persist
        return this.save(flow, CrudEventType.UPDATE);
    }

    @SneakyThrows({QueueException.class, FlowProcessingException.class})
    @VisibleForTesting
    public FlowWithSource save(GenericFlow flow, CrudEventType crudEventType) throws ConstraintViolationException {

        // Inject default plugin 'version' props before converting
        // to flow to correctly resolve to plugin type - this is to ensure the flow is parseable before saving.
        FlowWithSource flowWithSource = pluginDefaultService.injectVersionDefaults(flow, false);

        // Check whether existing Flow is equal.
        FlowWithSource nullOrExisting = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId()).orElse(null);
        if (nullOrExisting != null && nullOrExisting.isSameWithSource(flow)) {
            return nullOrExisting;
        }

        // Update revision
        List<FlowWithSource> revisions = this.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId());
        final int revision = revisions.isEmpty() ? 1 : revisions.getLast().getRevision() + 1;

        flow = flow.toBuilder().revision(revision).build();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flow);
        fields.put(field("source_code"), flow.getSource());

        this.jdbcRepository.persist(flow, fields);

        flowQueue.emit(flow);
        eventPublisher.publishEvent(new CrudEvent<>(flow, nullOrExisting, crudEventType));

        return flowWithSource.toBuilder().revision(revision).build();
    }

    @SneakyThrows
    @Override
    public FlowWithSource delete(FlowInterface flow) {
        Optional<FlowWithSource> existing = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.ofNullable(flow.getRevision()));
        if (existing.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        Optional<FlowWithSource> last = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId());
        if (last.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        if (!last.get().getRevision().equals(existing.get().getRevision())) {
            throw new IllegalStateException("Trying to deleted old revision, wanted " + existing.get().getRevision() + ", last revision is " + last.get().getRevision());
        }

        FlowWithSource deleted = existing.get().toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted.toFlow());
        fields.put(field("source_code"), deleted.getSource());

        this.jdbcRepository.persist(deleted, fields);

        flowQueue.emit(deleted);
        eventPublisher.publishEvent(CrudEvent.delete(flow));

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
    public Flux<Flow> findAsync(String tenantId, List<QueryFilter> filters) {
        return this.findAsync(tenantId, filters, Resource.FLOW);
    }

    protected Flux<Flow> findAsync(String tenantId, @Nullable List<QueryFilter> filters, QueryFilter.Resource resource) {
        if (filters == null || filters.isEmpty()) {
            return findAsync(defaultFilter(tenantId), null);
        }
        Condition condition = this.filter(filters, null, resource);
        return findAsync(defaultFilter(tenantId), condition);
    }

    protected Flux<Flow> findAsync(Condition defaultFilter, Condition condition, OrderField<Flow>... orderByFields) {
        return Flux.create(emitter -> this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(defaultFilter);

                if (condition != null) {
                    select = select.and(condition);
                }

                if (orderByFields != null) {
                    select.orderBy(orderByFields);
                }

                try (Stream<Record4<String, String, String, String>> stream =  select.fetchSize(FETCH_SIZE).stream()){
                    stream
                        .map(record -> (Flow) jdbcRepository.map(record))
                        .forEach(emitter::next);
                } finally {
                    emitter.complete();
                }
            }), FluxSink.OverflowStrategy.BUFFER);
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

    @Override
    public Boolean existAnyNoAcl(String tenantId){
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                return context.fetchExists(context
                    .selectOne()
                    .from(jdbcRepository.getTable())
                    .where(defaultFilterWithNoACL(tenantId, false)));
            });
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Flows.Fields, ? extends ColumnDescriptor<Flows.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<String, ? extends ColumnDescriptor<Flows.Fields>> columnsWithoutDate = descriptors.getColumns().entrySet().stream()
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

                List<? extends ColumnDescriptor<Flows.Fields>> columnsWithoutDateWithOutAggs = columnsWithoutDate.values().stream()
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

    public Double fetchValue(String tenantId, DataFilterKPI<Flows.Fields, ? extends ColumnDescriptor<Flows.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate, boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<Flows.Fields> columnDescriptor = dataFilter.getColumns();
            String columnKey = this.getFieldsMapping().get(columnDescriptor.getField());
            Field<?> field = columnToField(columnDescriptor, getFieldsMapping());
            if (columnDescriptor.getAgg() != null) {
                field = filterService.buildAggregation(field, columnDescriptor.getAgg());
            }

            List<AbstractFilter<Flows.Fields>> filters = new ArrayList<>(ListUtils.emptyOnNull(dataFilter.getWhere()));
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

            return result != null ? result.getValue(field, Double.class) : null;
        });
    }
}
