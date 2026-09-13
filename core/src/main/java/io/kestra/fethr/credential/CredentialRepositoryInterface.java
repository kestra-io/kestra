package io.kestra.fethr.credential;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Persistence for {@link Credential}.
 *
 * <p>
 * Every lookup is scoped by tenant and matches {@code namespace} exactly. That is the storage
 * contract, not the resolution policy: {@link CredentialFunction} walks the flow's parent namespace
 * tree and asks here once per level, so a credential on a parent namespace <em>is</em> inherited by
 * a flow beneath it. Secrets resolve by exact match at both layers; credentials do not.
 */
public interface CredentialRepositoryInterface {

    /**
     * Finds a credential by its full address. Exact namespace match.
     */
    Optional<Credential> findByName(String tenantId, String namespace, String name);

    /**
     * All credentials of a namespace. Exact match, so this excludes child namespaces.
     */
    List<Credential> findByNamespace(String tenantId, String namespace);

    /**
     * All credentials of a tenant of one type, across namespaces. This is what backs the credential
     * picker a {@link CredentialProperty} renders.
     */
    List<Credential> findByType(String tenantId, CredentialType type);

    /**
     * All credentials of a tenant, across namespaces.
     */
    List<Credential> findAll(String tenantId);

    /**
     * A page of credentials for the administration UI, honouring 2.0's {@link QueryFilter}
     * contract so filtering, sorting and paging behave like every other table.
     */
    ArrayListTotal<Credential> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters);

    Credential save(Credential credential);

    /**
     * Updates a credential in place. Rejects a change of name, namespace or type.
     *
     * @see Credential#validateUpdate(Credential)
     */
    Credential update(Credential credential, Credential previous);

    /**
     * Soft-deletes a credential, returning the deleted row, or empty if there was nothing to delete.
     */
    Optional<Credential> delete(String tenantId, String namespace, String name);
}
