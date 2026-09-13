package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Optional;

import org.jooq.Condition;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.fethr.credential.Credential;
import io.kestra.fethr.credential.CredentialRepositoryInterface;
import io.kestra.fethr.credential.CredentialType;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * JDBC persistence for {@link Credential}.
 *
 * <p>
 * Built on {@link AbstractJdbcCrudRepository}, which supplies save, update, soft delete and paging,
 * so this adds only the lookups specific to a credential's address and type.
 *
 * <p>
 * The 1.x fork emitted every write to a named {@code credential} queue. Queue 2.0 dropped the
 * {@code queue_type} enum it was registered through and nothing consumed it, so that is gone rather
 * than ported -- the same call made for secrets.
 */
public abstract class AbstractJdbcCredentialRepository extends AbstractJdbcCrudRepository<Credential> implements CredentialRepositoryInterface {

    protected AbstractJdbcCredentialRepository(io.kestra.jdbc.AbstractJdbcRepository<Credential> jdbcRepository) {
        super(jdbcRepository);
    }

    abstract protected Condition findCondition(String query);

    @Override
    protected Condition findQueryCondition(String query) {
        return findCondition(query);
    }

    @Override
    public Optional<Credential> findByName(String tenantId, String namespace, String name) {
        return findOne(
            tenantId,
            field("namespace").eq(namespace).and(field("name").eq(name))
        );
    }

    @Override
    public List<Credential> findByNamespace(String tenantId, String namespace) {
        return find(tenantId, field("namespace").eq(namespace));
    }

    @Override
    public List<Credential> findByType(String tenantId, CredentialType type) {
        return find(tenantId, field("type").eq(type.name()));
    }

    @Override
    public ArrayListTotal<Credential> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters) {
        return findPage(
            pageable,
            tenantId,
            this.filter(filters, "updated", QueryFilter.Resource.CREDENTIALS)
        );
    }

    @Override
    public Credential update(Credential credential, Credential previous) {
        findByName(previous.getTenantId(), previous.getNamespace(), previous.getName())
            .flatMap(current -> current.validateUpdate(credential))
            .ifPresent(violation ->
            {
                throw violation;
            });

        return super.update(credential);
    }

    @Override
    public Optional<Credential> delete(String tenantId, String namespace, String name) {
        return findByName(tenantId, namespace, name).map(this::delete);
    }
}
