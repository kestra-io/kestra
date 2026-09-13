package io.kestra.fethr.secret;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Persistence for {@link Secret}.
 *
 * <p>
 * Every lookup is scoped by tenant and matches {@code namespace} exactly: a secret in
 * {@code fethr.hl7} is not reachable from {@code fethr.billing}, and nothing walks up the namespace
 * hierarchy. {@link FethrSecretService} depends on that being true.
 */
public interface SecretRepositoryInterface {

    /**
     * Finds a secret by its full address. Exact namespace match.
     */
    Optional<Secret> findByKey(String tenantId, String namespace, String key);

    /**
     * All secrets of a namespace. Exact match, so this does not include child namespaces.
     */
    List<Secret> findByNamespace(String tenantId, String namespace);

    /**
     * All secrets of a tenant, across namespaces.
     */
    List<Secret> findAll(String tenantId);

    /**
     * A page of secrets for the administration UI, honouring 2.0's {@link QueryFilter} contract so
     * the secrets table's filter bar, sorting and paging work unchanged.
     */
    ArrayListTotal<Secret> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters);

    /**
     * Creates a secret.
     */
    Secret save(Secret secret);

    /**
     * Updates a secret in place. Rejects a change of key or namespace, which would really be a
     * different secret.
     *
     * @see Secret#validateUpdate(Secret)
     */
    Secret update(Secret secret, Secret previous);

    /**
     * Soft-deletes a secret, returning the deleted row, or empty if there was nothing to delete.
     */
    Optional<Secret> delete(String tenantId, String namespace, String key);
}
