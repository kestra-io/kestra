package io.kestra.jdbc.repository;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

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
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.SourceSearchMatcher;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Flows;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Slf4j
public abstract class AbstractJdbcFlowRepository extends AbstractJdbcRepository implements FlowRepositoryInterface {

    protected static final ObjectMapper MAPPER = JdbcMapper.of();

    private static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);
    public static final Field<String> SOURCE_FIELD = field("source_code", String.class);
    public static final Field<Integer> REVISION_FIELD = field("revision", Integer.class);
    private static final Field<Boolean> DISABLED_FIELD = field("disabled", Boolean.class);

    private final ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher;
    private final ModelValidator modelValidator;
    private final FlowParsingService flowParsingService;

    private final JdbcFilterService filterService;

    protected io.kestra.jdbc.AbstractJdbcRepository<FlowInterface> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractJdbcFlowRepository(
        io.kestra.jdbc.AbstractJdbcRepository<FlowInterface> jdbcRepository,
        ModelValidator modelValidator,
        ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher,
        FlowParsingService flowParsingService,
        JdbcFilterService filterService) {
        this.jdbcRepository = jdbcRepository;
        this.modelValidator = modelValidator;
        this.eventPublisher = eventPublisher;
        this.flowParsingService = flowParsingService;
        this.jdbcRepository.setDeserializer(record ->
        {
            String source = record.get("value", String.class);
            String namespace = record.get("namespace", String.class);
            String tenantId = record.get(TENANT_ID_FIELD);
            try {
                Map<String, Object> map = MAPPER.readValue(source, new TypeReference<>() {
                });

                // Inject default plugin 'version' props before converting
                // to flow to correctly resolve to plugin type.
                map = flowParsingService.injectPluginVersions(tenantId, namespace, map);

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
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var from = revision.map(
                    integer -> context
                        .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                        .from(jdbcRepository.getTable())
                        .where(this.defaultFilter(tenantId, true))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(
                            REVISION_FIELD.eq(integer)
                        )
                ).orElseGet(
                    () -> context
                        .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                        .from(fromLastRevision(true))
                        .where(this.defaultFilter(tenantId, Boolean.TRUE.equals(allowDeleted)))
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
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var from = revision
                    .map(
                        integer -> context
                            .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                            .from(jdbcRepository.getTable())
                            .where(this.defaultFilterWithNoACL(tenantId, true))
                            .and(NAMESPACE_FIELD.eq(namespace))
                            .and(field("id", String.class).eq(id))
                            .and(REVISION_FIELD.eq(integer))
                    ).orElseGet(
                        () -> context
                            .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                            .from(fromLastRevision(true))
                            .where(this.defaultFilterWithNoACL(tenantId, true))
                            .and(NAMESPACE_FIELD.eq(namespace))
                            .and(field("id", String.class).eq(id))
                    );

                return this.jdbcRepository.fetchOne(from).map(it -> (Flow) it);
            });
    }

    protected Table<Record> fromLastRevision(boolean asterisk) {
        return JdbcFlowRepositoryService.lastRevision(jdbcRepository, asterisk);
    }

    protected Table<Record> fromLastNonDraftRevision(boolean asterisk) {
        return JdbcFlowRepositoryService.lastNonDraftRevision(jdbcRepository, asterisk);
    }

    protected Condition noAclDefaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    // "executable" filtering must stay independent of read-ACL, since users with
    // execute-but-not-read permission are exactly who these two methods serve.
    protected Condition defaultExecutionFilter(String tenantId) {
        return this.defaultFilterWithNoACL(tenantId).and(DISABLED_FIELD.eq(false));
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var from = revision.map(
                    integer -> context
                        .select(
                            SOURCE_FIELD,
                            VALUE_FIELD,
                            NAMESPACE_FIELD,
                            TENANT_ID_FIELD
                        )
                        .from(jdbcRepository.getTable())
                        .where(this.defaultFilter(tenantId, true))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(REVISION_FIELD.eq(integer))
                )
                    .orElseGet(
                        () -> context
                            .select(
                                SOURCE_FIELD,
                                VALUE_FIELD,
                                NAMESPACE_FIELD,
                                TENANT_ID_FIELD
                            )
                            .from(fromLastRevision(true))
                            .where(this.defaultFilter(tenantId, Boolean.TRUE.equals(allowDeleted)))
                            .and(NAMESPACE_FIELD.eq(namespace))
                            .and(field("id", String.class).eq(id))
                    );

                Record4<String, Object, String, String> fetched = from.fetchAny();

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
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var from = revision.map(
                    integer -> context
                        .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                        .from(jdbcRepository.getTable())
                        .where(this.defaultFilterWithNoACL(tenantId, true))
                        .and(NAMESPACE_FIELD.eq(namespace))
                        .and(field("id", String.class).eq(id))
                        .and(REVISION_FIELD.eq(integer))
                )
                    .orElseGet(
                        () -> context
                            .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                            .from(fromLastRevision(true))
                            .where(this.defaultFilterWithNoACL(tenantId, true))
                            .and(NAMESPACE_FIELD.eq(namespace))
                            .and(field("id", String.class).eq(id))
                    );
                Record4<String, Object, String, String> fetched = from.fetchAny();

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
    public Optional<Flow> findByIdForExecution(String tenantId, String namespace, String id) {
        return findByIdForExecution(tenantId, namespace, id, this.defaultFilter(tenantId));
    }

    @Override
    public Optional<Flow> findByIdForExecutionWithoutAcl(String tenantId, String namespace, String id) {
        return findByIdForExecution(tenantId, namespace, id, this.defaultFilterWithNoACL(tenantId));
    }

    private Optional<Flow> findByIdForExecution(String tenantId, String namespace, String id, Condition tenantCondition) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                    .from(fromLastNonDraftRevision(true))
                    .where(tenantCondition)
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id));

                return this.jdbcRepository.fetchOne(select).map(it -> (Flow) it);
            });
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSourceForExecution(String tenantId, String namespace, String id) {
        return findByIdWithSourceForExecution(tenantId, namespace, id, this.defaultFilter(tenantId));
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSourceForExecutionWithoutAcl(String tenantId, String namespace, String id) {
        return findByIdWithSourceForExecution(tenantId, namespace, id, this.defaultFilterWithNoACL(tenantId));
    }

    private Optional<FlowWithSource> findByIdWithSourceForExecution(String tenantId, String namespace, String id, Condition tenantCondition) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                var select = context
                    .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                    .from(fromLastNonDraftRevision(true))
                    .where(tenantCondition)
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id));

                Record4<String, Object, String, String> fetched = select.fetchAny();

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
    public List<FlowWithSource> findAllWithSourceForExecutionForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("source_code"),
                        field("namespace"),
                        TENANT_ID_FIELD
                    )
                    .from(fromLastNonDraftRevision(true))
                    .where(this.defaultFilter());

                // Same robust deserialization as findAllWithSourceForAllTenants(): we don't want
                // a single broken plugin in the JSON to crash scheduler bootstrap.
                return select.fetch().stream().map(record ->
                {
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
    public List<FlowWithSource> findRevisions(String tenantId, String namespace, String id, Boolean allowDeleted) {
        return findRevisions(tenantId, namespace, id, allowDeleted, null);
    }

    @Override
    public List<FlowWithSource> findRevisions(String tenantId, String namespace, String id, Boolean allowDeleted, List<Integer> revisions) {
        return findRevisions(namespace, id, revisions, this.defaultFilter(tenantId, Boolean.TRUE.equals(allowDeleted)));
    }

    @Override
    public List<FlowWithSource> findRevisionsWithoutAcl(String tenantId, String namespace, String id, Boolean allowDeleted, List<Integer> revisions) {
        return findRevisions(namespace, id, revisions, this.defaultFilterWithNoACL(tenantId, Boolean.TRUE.equals(allowDeleted)));
    }

    private List<FlowWithSource> findRevisions(String namespace, String id, List<Integer> revisions, Condition baseFilter) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                Condition tenantAndRevisionCondition = baseFilter;
                if (!ListUtils.isEmpty(revisions)) {
                    tenantAndRevisionCondition = tenantAndRevisionCondition.and(REVISION_FIELD.in(revisions));
                }
                Select<Record4<String, Object, String, String>> select = DSL
                    .using(configuration)
                    .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(tenantAndRevisionCondition)
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(field("id", String.class).eq(id))
                    .orderBy(REVISION_FIELD.asc());

                return select.fetch()
                    .map(record -> FlowWithSource.of((Flow) jdbcRepository.map(record), record.get(SOURCE_FIELD)));
            });
    }

    @Override
    public int count(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .selectCount()
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId))
                    .fetchOne(0, int.class)
            );
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Flow> findAll(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("namespace"),
                        TENANT_ID_FIELD
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
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("namespace"),
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                List<Flow> flows = new ArrayList<>();
                select.fetch().forEach(
                    item ->
                    {
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
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("source_code"),
                        field("namespace"),
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter(tenantId));

                return select.fetch().map(
                    record -> FlowWithSource.of(
                        (Flow) jdbcRepository.map(record),
                        record.get(SOURCE_FIELD)
                    )
                );
            });
    }

    @Override
    public List<FlowWithSource> findAllWithSourceWithNoAcl(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("source_code"),
                        field("namespace"),
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(this.noAclDefaultFilter(tenantId));

                return select.fetch().map(
                    record -> FlowWithSource.of(
                        (Flow) jdbcRepository.map(record),
                        record.get(SOURCE_FIELD)
                    )
                );
            });
    }

    @Override
    public List<FlowWithSource> findAllWithSourceForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        VALUE_FIELD,
                        field("source_code"),
                        field("namespace"),
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(this.defaultFilter());

                // findAllWithSourceForAllTenants() is used in the backend, so we want it to work even if messy plugins exist.
                // That's why we will try to deserialize each flow and log an error but not crash in case of exception.
                return select.fetch().stream().map(record ->
                {
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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Flow> findByNamespace(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectConditionStep<Record3<Object, Object, String>> select = findByNamespaceSelect(namespace)
                    .and(this.defaultFilter(tenantId));

                return (List) this.jdbcRepository.fetch(select);
            });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectConditionStep<Record3<Object, Object, Object>> select = findByNamespacePrefixSelect(namespacePrefix)
                    .and(this.defaultFilter(tenantId));

                return (List) this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectConditionStep<Record3<Object, Object, String>> select = findByNamespaceSelect(namespace)
                    .and(this.defaultExecutionFilter(tenantId));

                return this.jdbcRepository.fetch(select);
            }).stream().map(it -> (Flow) it).map(FlowForExecution::of).toList();
    }

    private SelectConditionStep<Record3<Object, Object, String>> findByNamespaceSelect(String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .select(VALUE_FIELD, field("namespace"), TENANT_ID_FIELD)
                    .from(fromLastRevision(true))
                    .where(NAMESPACE_FIELD.eq(namespace))
            );
    }

    private SelectConditionStep<Record3<Object, Object, Object>> findByNamespacePrefixSelect(String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .select(field("value"), field("namespace"), field("tenant_id"))
                    .from(fromLastRevision(true))
                    .where(NAMESPACE_FIELD.eq(namespacePrefix).or(NAMESPACE_FIELD.startsWith(namespacePrefix + ".")))
            );
    }

    @Override
    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        SOURCE_FIELD,
                        VALUE_FIELD,
                        NAMESPACE_FIELD,
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(NAMESPACE_FIELD.eq(namespace))
                    .and(this.defaultFilter(tenantId));

                return select.fetch().map(
                    record -> FlowWithSource.of(
                        (Flow) jdbcRepository.map(record),
                        record.get(SOURCE_FIELD)
                    )
                );
            });
    }

    @Override
    public List<FlowWithSource> findByNamespacePrefixWithSource(String tenantId, String namespacePrefix) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(
                        SOURCE_FIELD,
                        VALUE_FIELD,
                        NAMESPACE_FIELD,
                        TENANT_ID_FIELD
                    )
                    .from(fromLastRevision(true))
                    .where(DSL.or(NAMESPACE_FIELD.eq(namespacePrefix), NAMESPACE_FIELD.startsWith(namespacePrefix + ".")))
                    .and(this.defaultFilter(tenantId));

                return select.fetch().map(
                    record -> FlowWithSource.of(
                        (Flow) jdbcRepository.map(record),
                        record.get(SOURCE_FIELD)
                    )
                );
            });
    }

    @SuppressWarnings("unchecked")
    private <R extends Record, E> SelectConditionStep<R> fullTextSelect(String tenantId, DSLContext context, List<Field<Object>> field) {
        ArrayList<Field<?>> fields = new ArrayList<>();
        // add mandatory fields
        fields.add(VALUE_FIELD);
        fields.add(TENANT_ID_FIELD);
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
                    .and(
                        DSL.field(DSL.quotedName("ft", "revision")).eq(
                            DSL.field(
                                DSL.quotedName(
                                    "rev",
                                    "revision"
                                )
                            )
                        )
                    )
            )
            .where(this.defaultFilter(tenantId));
    }

    abstract protected Condition findCondition(String query, Map<String, String> labels);

    protected Condition findQueryCondition(String query) {
        return findCondition(query, Map.of());
    }

    abstract protected Condition findCondition(Object value, QueryFilter.Op operation);

    @Override
    public Condition findLabelCondition(Either<Map<?, ?>, String> value, QueryFilter.Op operation) {
        return findCondition(value.isLeft() ? value.getLeft() : value.getRight(), operation);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayListTotal<Flow> find(Pageable pageable, @Nullable String tenantId, @Nullable List<QueryFilter> filters) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = getFindFlowSelect(tenantId, filters, context, null);

                return (ArrayListTotal) this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String tenantId,
        String namespace,
        @Nullable Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                return (ArrayListTotal) this.jdbcRepository.fetchPage(
                    context,
                    getFindFlowSelect(tenantId, null, context, null)
                        .and(findTriggerClassCondition(triggerClass))
                        .and(NAMESPACE_FIELD.eq(namespace)),
                    pageable
                );
            });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                return (ArrayListTotal) this.jdbcRepository.fetchPage(
                    context,
                    getFindFlowSelect(tenantId, null, context, null)
                        .and(findTriggerClassCondition(triggerClass)),
                    pageable
                );
            });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayListTotal<Flow> findWithNoAcl(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                ArrayList<Field<?>> fields = new ArrayList<>();
                fields.add(VALUE_FIELD);
                fields.add(TENANT_ID_FIELD);
                fields.add(field("namespace"));
                SelectConditionStep<Record> select = context
                    .select(fields)
                    .from(fromLastRevision(false))
                    .join(jdbcRepository.getTable().as("ft"))
                    .on(
                        DSL.field(DSL.quotedName("ft", "key")).eq(DSL.field(DSL.field(DSL.quotedName("rev", "key"))))
                            .and(DSL.field(DSL.quotedName("ft", "revision")).eq(DSL.field(DSL.quotedName("rev", "revision"))))
                    )
                    .where(this.defaultFilterWithNoACL(tenantId, false))
                    .and(findTriggerClassCondition(triggerClass));
                return (ArrayListTotal) this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayListTotal<FlowWithSource> findWithSource(Pageable pageable, @Nullable String tenantId, @Nullable List<QueryFilter> filters) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
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

    protected Name getColumnName(QueryFilter.Field field) {
        if (QueryFilter.Field.FLOW_ID.equals(field)) {
            return DSL.quotedName("id");
        } else {
            return DSL.quotedName(field.name().toLowerCase());
        }
    }

    abstract protected Condition findSourceCodeCondition(String query);

    abstract protected Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass);

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, boolean caseSensitive, boolean wholeWord, boolean regex, SourceSearchScope scope, @Nullable String tenantId, @Nullable String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record> select = this.fullTextSelect(tenantId, context, Collections.singletonList(field("source_code")));

                if (query != null && !regex) {
                    select = select.and(this.findSourceCodeCondition(query));
                }

                if (namespace != null) {
                    select = select.and(DSL.or(NAMESPACE_FIELD.eq(namespace), NAMESPACE_FIELD.startsWith(namespace + ".")));
                }

                List<SearchResult<Flow>> results = select
                    .limit(SourceSearchMatcher.MAX_SOURCE_SEARCH_CANDIDATES)
                    .fetch()
                    .stream()
                    .map(record -> new SearchResult<>(
                        (Flow) this.jdbcRepository.map(record),
                        query == null
                            ? List.<SourceMatch>of()
                            : SourceSearchMatcher.findMatches(record.getValue("source_code", String.class), query, caseSensitive, wholeWord, regex, scope),
                        true
                    ))
                    .filter(result -> query == null || !result.getMatches().isEmpty())
                    .sorted(java.util.Comparator.comparing((SearchResult<Flow> r) -> r.getModel().getNamespace())
                        .thenComparing(r -> r.getModel().getId()))
                    .toList();

                return pageable == null || pageable.getSize() == -1
                    ? new ArrayListTotal<>(results, results.size())
                    : ArrayListTotal.of(pageable, results);
            });
    }

    @Override
    public FlowWithSource create(GenericFlow flow) throws ConstraintViolationException {
        if (this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(
                Collections.singleton(
                    ManualConstraintViolation.of(
                        "Flow id already exists",
                        flow,
                        GenericFlow.class,
                        "flow.id",
                        flow.getId()
                    )
                )
            );
        }
        return this.save(flow, CrudEventType.CREATE);
    }

    @SneakyThrows({ FlowProcessingException.class })
    @Override
    public FlowWithSource update(GenericFlow flow, FlowInterface previous) throws ConstraintViolationException {
        try {
            // For drafts the YAML may be unparsable; if parsing fails we skip all
            // validation since draft revisions are intentionally allowed to carry invalid content.
            FlowWithSource flowWithDefault = flowParsingService.parse(flow, false);
            // Drafts are allowed to be saved invalid - they will fail at execution time instead.
            // Read the draft flag from the original GenericFlow (set from the API draft flag) rather
            // than from flowWithDefault, since `parse` re-parses the YAML source which
            // does not carry the draft field.
            if (!flow.isDraft()) {
                modelValidator.validate(flowWithDefault);
            }

            Flow previousFlow;
            if (previous instanceof Flow o) {
                previousFlow = o;
            } else {
                previousFlow = flowParsingService.parse(previous, false);
            }

            // Check update
            Optional<ConstraintViolationException> checkUpdate = previousFlow.validateUpdate(flowWithDefault);
            if (checkUpdate.isPresent()) {
                throw checkUpdate.get();
            }
        } catch (FlowProcessingException e) {
            if (!flow.isDraft()) {
                throw e;
            }
            // Draft with unparsable YAML: skip validation entirely.
        }

        // Persist
        return this.save(flow, CrudEventType.UPDATE);
    }

    @SneakyThrows(FlowProcessingException.class)
    @VisibleForTesting
    public FlowWithSource save(GenericFlow flow, CrudEventType crudEventType) throws ConstraintViolationException {

        // Ensure the flow is parseable before saving.
        // For drafts with unparsable YAML, fall back to a FlowWithException so the raw source can
        // still be persisted without throwing.
        FlowWithSource flowWithSource;
        try {
            flowWithSource = flowParsingService.parse(flow, false);
        } catch (FlowProcessingException e) {
            if (!flow.isDraft()) {
                throw e;
            }
            flowWithSource = FlowWithException.from(flow, e);
        }

        // Check whether existing Flow is equal.
        FlowWithSource nullOrExisting = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId()).orElse(null);
        if (nullOrExisting != null && nullOrExisting.isSameWithSource(flow)) {
            return nullOrExisting;
        }

        // Update revision
        List<FlowWithSource> revisions = this.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId(), true);
        final int revision = revisions.isEmpty() ? 1 : revisions.getLast().getRevision() + 1;

        flow = flow.toBuilder().revision(revision).updated(Instant.now()).build();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(flow);
        fields.put(field("source_code"), flow.getSource());

        this.jdbcRepository.persist(flow, fields);

        eventPublisher.publishEvent(new CrudEvent<>(flow, nullOrExisting, crudEventType));

        // draft is not part of the YAML source so parsing loses it; restore from the original flow.
        return flowWithSource.toBuilder()
            .revision(revision)
            .draft(flow.isDraft())
            .build();
    }

    @SneakyThrows
    @Override
    public FlowWithSource delete(FlowInterface flow) {
        Optional<FlowWithSource> existing = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.ofNullable(flow.getRevision()));
        FlowWithSource existingFlow = existing
            .orElseThrow(() -> new IllegalStateException("Flow " + flow.getId() + " doesn't exists"));

        Optional<FlowWithSource> last = this.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId());
        if (last.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        if (!last.get().getRevision().equals(existingFlow.getRevision())) {
            throw new IllegalStateException("Trying to deleted old revision, wanted " + existingFlow.getRevision() + ", last revision is " + last.get().getRevision());
        }

        return deleteFlow(flow, existingFlow);
    }

    @SneakyThrows
    @Override
    public FlowWithSource deleteWithoutAcl(FlowInterface flow) {
        Optional<FlowWithSource> existing = this.findByIdWithSourceWithoutAcl(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.ofNullable(flow.getRevision()));
        FlowWithSource existingFlow = existing
            .orElseThrow(() -> new IllegalStateException("Flow " + flow.getId() + " doesn't exists"));

        Optional<FlowWithSource> last = this.findByIdWithSourceWithoutAcl(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.empty());
        if (last.isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        if (!last.get().getRevision().equals(existingFlow.getRevision())) {
            throw new IllegalStateException("Trying to deleted old revision, wanted " + existingFlow.getRevision() + ", last revision is " + last.get().getRevision());
        }

        return deleteFlow(flow, existingFlow);
    }

    @SneakyThrows
    @Override
    public void deleteRevisions(String tenantId, String namespace, String id, List<Integer> revisions) {
        List<FlowWithSource> flows = findRevisions(tenantId, namespace, id, true, revisions);
        Integer last = lastRevision(tenantId, namespace, id);
        FlowWithSource lastFlow = null;
        HashMap<FlowInterface, Map<Field<Object>, Object>> revisionsToDelete = new HashMap<>();
        for (FlowWithSource flow : flows) {
            if (Objects.equals(flow.getRevision(), last)) {
                lastFlow = flow;
            } else {
                FlowWithSource toDelete = flow.toBuilder().deleted(true).build();
                Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(toDelete.toFlow());
                fields.put(field("source_code"), flow.getSource());
                revisionsToDelete.put(toDelete, fields);
            }
        }

        this.jdbcRepository.persistBatch(revisionsToDelete);

        if (lastFlow != null) {
            deleteFlow(lastFlow, lastFlow);
        }
    }

    private FlowWithSource deleteFlow(FlowInterface flow, FlowWithSource existingFlow)
        throws QueueException {
        FlowWithSource deleted = existingFlow.toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted.toFlow());
        fields.put(field("source_code"), deleted.getSource());

        this.jdbcRepository.persist(deleted, fields);

        eventPublisher.publishEvent(CrudEvent.delete(flow));
        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
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
            .transactionResult(
                configuration -> DSL
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

    protected final Flux<Flow> findAsync(Condition defaultFilter, Condition condition, OrderField<Flow>... orderByFields) {
        return Flux.create(
            emitter -> this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration ->
                {
                    DSLContext context = DSL.using(configuration);

                    var select = context
                        .select(SOURCE_FIELD, VALUE_FIELD, NAMESPACE_FIELD, TENANT_ID_FIELD)
                        .from(this.jdbcRepository.getTable())
                        .where(defaultFilter);

                    if (condition != null) {
                        select = select.and(condition);
                    }

                    var fetchQuery = orderByFields != null
                        ? select.orderBy(orderByFields).fetchSize(FETCH_SIZE)
                        : select.fetchSize(FETCH_SIZE);

                    try (var stream = fetchQuery.stream()) {
                        stream
                            .map(record -> (Flow) jdbcRepository.map(record))
                            .forEach(emitter::next);
                    } finally {
                        emitter.complete();
                    }
                }),
            FluxSink.OverflowStrategy.BUFFER
        );
    }

    @Override
    public Integer lastRevision(String tenantId, String namespace, String id) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .fetchValue(
                        DSL.select(REVISION_FIELD)
                            .from(fromLastRevision(true))
                            .where(this.defaultFilter(tenantId))
                            .and(NAMESPACE_FIELD.eq(namespace))
                            .and(field("id", String.class).eq(id))
                            .limit(1)
                    )
            );
    }

    @Override
    public Boolean existAnyNoAcl(String tenantId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                return context.fetchExists(
                    context
                        .selectOne()
                        .from(jdbcRepository.getTable())
                        .where(defaultFilterWithNoACL(tenantId, false))
                );
            });
    }

    @Override
    public ArrayListTotal<Map<String, Object>> fetchData(
        String tenantId,
        DataFilter<Flows.Fields, ? extends ColumnDescriptor<Flows.Fields>> descriptors,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
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
                    fromLastRevision(true),
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

    public Double fetchValue(String tenantId, DataFilterKPI<Flows.Fields, ? extends ColumnDescriptor<Flows.Fields>> dataFilter, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean numeratorFilter) {
        return this.jdbcRepository.getDslContextWrapper().transactionResult(configuration ->
        {
            DSLContext context = DSL.using(configuration);
            ColumnDescriptor<Flows.Fields> columnDescriptor = dataFilter.getColumns();
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
                .from(fromLastRevision(true))
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
