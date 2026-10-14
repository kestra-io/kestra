package io.kestra.core.repositories;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.QueryFilter;

/**
 * Access-control collaborator for namespace-scoped repositories. A repository consults it at its
 * {@code defaultFilter} chokepoint and translates the returned {@link AccessScope} into its own query
 * language (a jOOQ condition, an Elasticsearch query). This keeps the access-control <em>policy</em> in
 * one place while the <em>translation</em> stays in each dialect-aware backend, so there is no
 * per-repository ACL subclass.
 */
public interface NamespaceAccessControl {

    /**
     * A no-op collaborator granting global access, the safe default for OSS, which has no namespace ACL.
     */
    NamespaceAccessControl GLOBAL = resource -> AccessScope.global();

    /**
     * @param resource the resource being read.
     * @return the namespaces the current caller may see for {@code resource}: global, a set, or none.
     */
    AccessScope namespaceScope(QueryFilter.Resource resource);
}
