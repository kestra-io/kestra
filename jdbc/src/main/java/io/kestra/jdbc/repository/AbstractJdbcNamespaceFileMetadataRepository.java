package io.kestra.jdbc.repository;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.TenantAndNamespace;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractJdbcNamespaceFileMetadataRepository extends AbstractJdbcRepository implements NamespaceFileMetadataRepositoryInterface {
    protected final io.kestra.jdbc.AbstractJdbcRepository<NamespaceFileMetadata> jdbcRepository;

    public AbstractJdbcNamespaceFileMetadataRepository(
        io.kestra.jdbc.AbstractJdbcRepository<NamespaceFileMetadata> jdbcRepository
    ) {
        this.jdbcRepository = jdbcRepository;
    }

    private static Condition lastCondition(boolean isLast) {
        return field("last").eq(isLast);
    }

    private static Condition lastCondition() {
        return lastCondition(true);
    }

    abstract protected Condition findCondition(String query);

    @Override
    protected Condition findQueryCondition(String query) {
        return findCondition(query);
    }

    @Override
    public Optional<NamespaceFileMetadata> findByPath(String tenantId, String namespace, String path) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId, true))
                    .and(field("namespace").eq(namespace))
                    .and(pathCondition(path))
                    .and(lastCondition());
                return this.jdbcRepository.fetchOne(from);
            });
    }

    private SelectConditionStep<Record1<Object>> findSelect(
        DSLContext context,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters,
        boolean allowDeleted,
        FetchVersion fetchBehavior
    ) {
        SelectConditionStep<Record1<Object>> condition = context
            .select(field("value"))
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter(tenantId, allowDeleted))
            .and(this.filter(filters, "updated", QueryFilter.Resource.NAMESPACE_FILE_METADATA));

        switch (fetchBehavior) {
            case LATEST -> condition = condition.and(lastCondition());
            case OLD -> condition = condition.and(lastCondition(false));
            case ALL -> condition = condition.and(field("last").in(true, false));
        }

        return condition;
    }

    @Override
    public ArrayListTotal<NamespaceFileMetadata> find(Pageable pageable, String tenantId, List<QueryFilter> filters, boolean allowDeleted, FetchVersion fetchBehavior) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = this.findSelect(
                    context,
                    tenantId,
                    filters,
                    allowDeleted,
                    fetchBehavior
                );

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public Integer purge(List<NamespaceFileMetadata> namespaceFilesMetadata) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<TenantAndNamespace, List<NamespaceFileMetadata>> byTenantNamespace = namespaceFilesMetadata.stream().collect(Collectors.toMap(
                    namespaceFileMetadata -> new TenantAndNamespace(namespaceFileMetadata.getTenantId(), namespaceFileMetadata.getNamespace()),
                    List::of,
                    (nsFile1, nsFile2) -> Stream.concat(nsFile1.stream(), nsFile2.stream()).toList()
                ));

                return byTenantNamespace.entrySet().stream().reduce(0, (totalForTenantNamespace, e) -> {
                    DeleteConditionStep<Record> deleteCondition = context.delete(this.jdbcRepository.getTable())
                        .where(this.defaultFilter(e.getKey().tenantId(), true))
                        .and(field("namespace").eq(e.getKey().namespace()))
                        .and(field("last").in(true, false));
                    if (e.getValue().getFirst().getVersion() == null) {
                        deleteCondition = deleteCondition.and(
                            field("path").in(namespaceFilesMetadata.stream()
                                .flatMap(namespaceFileMetadata -> Stream.of(namespaceFileMetadata.path(false), namespaceFileMetadata.path(true)))
                                .toList())
                        );
                    } else {
                        deleteCondition = deleteCondition.and(DSL.or(e.getValue().stream().map(namespaceFileMetadata -> DSL.and(
                            pathCondition(namespaceFileMetadata.getPath()),
                            field("version").eq(namespaceFileMetadata.getVersion()
                            ))).toList()));
                    }

                    int deletedAmount = deleteCondition.execute();

                    return totalForTenantNamespace + deletedAmount;
                }, Integer::sum);
            });
    }

    private static Condition pathCondition(String path) {
        return field("path").in(List.of(NamespaceFileMetadata.path(path, false), NamespaceFileMetadata.path(path, true)));
    }

    @Override
    public NamespaceFileMetadata save(NamespaceFileMetadata namespaceFileMetadata) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Optional<NamespaceFileMetadata> maybePrevious = this.findByPath(namespaceFileMetadata.getTenantId(), namespaceFileMetadata.getNamespace(), namespaceFileMetadata.getPath());
                NamespaceFileMetadata nsFileMetadataToPersist = namespaceFileMetadata.asLast().toBuilder().deleted(false).version(maybePrevious.map(previous -> {
                    if (previous.isDirectory()) {
                        // Directories stay at version 1
                        return 1;
                    }
                    return previous.getVersion() + 1;
                }).orElse(1)).created(maybePrevious.map(NamespaceFileMetadata::getCreated).orElse(Instant.now())).build();

                if (maybePrevious.isPresent()) {
                    NamespaceFileMetadata previous = maybePrevious.get();
                    if (namespaceFileMetadata.isDeleted()) {
                        // If we are deleting, we just mark the previous as deleted without changing version and we return directly
                        nsFileMetadataToPersist = previous.toDeleted();
                    } else {
                        // We mark the previous as not last
                        NamespaceFileMetadata previousAsNotLast = previous.toBuilder().last(false).build();
                        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(previousAsNotLast);
                        this.jdbcRepository.persist(previousAsNotLast, context, fields);
                    }
                }

                Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(nsFileMetadataToPersist);
                this.jdbcRepository.persist(nsFileMetadataToPersist, context, fields);

                return nsFileMetadataToPersist;
            });
    }
}
