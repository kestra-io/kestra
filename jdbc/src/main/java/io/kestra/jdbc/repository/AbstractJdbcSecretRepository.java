package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Optional;

import org.jooq.Condition;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.fethr.secret.Secret;
import io.kestra.fethr.secret.SecretRepositoryInterface;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * JDBC persistence for {@link Secret}.
 *
 * <p>
 * Built on {@link AbstractJdbcCrudRepository}, which already supplies save, update, soft delete,
 * paging and the tenant/deleted default filter -- so this only adds the lookups that are specific
 * to a secret's address.
 *
 * <p>
 * The 1.x fork emitted every write to a named {@code secret} queue. Queue 2.0 dropped the
 * {@code queue_type} enum that queue was registered through, and nothing ever consumed it, so the
 * emit is gone rather than ported.
 */
public abstract class AbstractJdbcSecretRepository extends AbstractJdbcCrudRepository<Secret> implements SecretRepositoryInterface {

    protected AbstractJdbcSecretRepository(io.kestra.jdbc.AbstractJdbcRepository<Secret> jdbcRepository) {
        super(jdbcRepository);
    }

    abstract protected Condition findCondition(String query);

    @Override
    protected Condition findQueryCondition(String query) {
        return findCondition(query);
    }

    @Override
    public Optional<Secret> findByKey(String tenantId, String namespace, String key) {
        return findOne(
            tenantId,
            field("namespace").eq(namespace).and(field("key").eq(key))
        );
    }

    @Override
    public List<Secret> findByNamespace(String tenantId, String namespace) {
        // Exact match: a secret on a parent namespace is not a secret of this one.
        return find(tenantId, field("namespace").eq(namespace));
    }

    @Override
    public ArrayListTotal<Secret> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters) {
        return findPage(
            pageable,
            tenantId,
            this.filter(filters, "updated", QueryFilter.Resource.SECRET_METADATA)
        );
    }

    @Override
    public Secret update(Secret secret, Secret previous) {
        findByKey(previous.getTenantId(), previous.getNamespace(), previous.getKey())
            .flatMap(current -> current.validateUpdate(secret))
            .ifPresent(violation ->
            {
                throw violation;
            });

        return super.update(secret);
    }

    @Override
    public Optional<Secret> delete(String tenantId, String namespace, String key) {
        return findByKey(tenantId, namespace, key).map(this::delete);
    }
}
