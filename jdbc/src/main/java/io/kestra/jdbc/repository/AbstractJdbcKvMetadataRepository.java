package io.kestra.jdbc.repository;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.TenantAndNamespace;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractJdbcKvMetadataRepository extends AbstractJdbcCrudRepository<PersistedKvMetadata> implements KvMetadataRepositoryInterface {

    public AbstractJdbcKvMetadataRepository(
        io.kestra.jdbc.AbstractJdbcRepository<PersistedKvMetadata> jdbcRepository,
        QueueService queueService
    ) {
        super(jdbcRepository, queueService);
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
    public Optional<PersistedKvMetadata> findByName(String tenantId, String namespace, String name) {
        var condition = field("namespace").eq(namespace)
            .and(field("name").eq(name))
            .and(lastCondition());
        return findOne(tenantId, condition, true);
    }

    private Condition findSelect(
        @Nullable List<QueryFilter> filters,
        boolean allowExpired,
        FetchVersion fetchBehavior
    ) {
        var condition = allowExpired ? DSL.trueCondition() : DSL.or(
            field("expiration_date").greaterThan(Instant.now()),
            field("expiration_date").isNull());

        condition = condition.and(this.filter(filters, "updated", QueryFilter.Resource.KV_METADATA));

        switch (fetchBehavior) {
            case LATEST -> condition = condition.and(lastCondition());
            case OLD -> condition = condition.and(lastCondition(false));
        }

        return condition;
    }

    @Override
    public ArrayListTotal<PersistedKvMetadata> find(Pageable pageable, String tenantId, List<QueryFilter> filters, boolean allowDeleted, boolean allowExpired, FetchVersion fetchBehavior) {
        var condition = findSelect(filters, allowExpired, fetchBehavior);
        return this.findPage(pageable, tenantId, condition, allowDeleted);
    }

    @Override
    public Integer purge(List<PersistedKvMetadata> persistedKvsMetadata) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Map<TenantAndNamespace, List<PersistedKvMetadata>> byTenantNamespace = persistedKvsMetadata.stream().collect(Collectors.toMap(
                    kvMetadata -> new TenantAndNamespace(kvMetadata.getTenantId(), kvMetadata.getNamespace()),
                    List::of,
                    (kv1, kv2) -> Stream.concat(kv1.stream(), kv2.stream()).toList()
                ));

                return byTenantNamespace.entrySet().stream().reduce(0, (totalForTenantNamespace, e) -> {
                    DeleteConditionStep<Record> deleteCondition = context.delete(this.jdbcRepository.getTable())
                        .where(this.defaultFilter(e.getKey().tenantId(), true))
                        .and(field("namespace").eq(e.getKey().namespace()))
                        .and(field("last").in(true, false));
                    if (e.getValue().getFirst().getVersion() == null) {
                        deleteCondition = deleteCondition.and(field("name").in(persistedKvsMetadata.stream().map(PersistedKvMetadata::getName).toList()));
                    } else {
                        deleteCondition = deleteCondition.and(DSL.or(e.getValue().stream().map(kvMetadata -> DSL.and(
                            field("name").eq(kvMetadata.getName()),
                            field("version").eq(kvMetadata.getVersion()
                            ))).toList()));
                    }

                    int deletedAmount = deleteCondition.execute();

                    return totalForTenantNamespace + deletedAmount;
                }, Integer::sum);
            });
    }

    @Override
    public PersistedKvMetadata save(PersistedKvMetadata kvMetadata) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                Optional<PersistedKvMetadata> maybePrevious = this.findByName(kvMetadata.getTenantId(), kvMetadata.getNamespace(), kvMetadata.getName());
                PersistedKvMetadata kvMetadataToPersist = kvMetadata.asLast().toBuilder().version(maybePrevious.map(PersistedKvMetadata::getVersion).orElse(0) + 1).build();
                if (maybePrevious.isPresent()) {
                    PersistedKvMetadata previous = maybePrevious.get();
                    if (kvMetadata.isDeleted()) {
                        // If we are deleting, we just mark the previous as deleted without changing version and we return directly
                        kvMetadataToPersist = previous.toBuilder().deleted(true).updated(Instant.now()).build();
                    } else {
                        // We mark the previous as not last
                        PersistedKvMetadata previousAsNotLast = previous.toBuilder().last(false).build();
                        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(previousAsNotLast);
                        this.jdbcRepository.persist(previousAsNotLast, context, fields);
                    }
                }

                Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(kvMetadataToPersist);
                this.jdbcRepository.persist(kvMetadataToPersist, context, fields);

                return kvMetadataToPersist;
            });
    }
}
