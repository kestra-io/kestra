package io.kestra.core.services;

import io.kestra.core.exceptions.ResourceAccessDeniedException;

/**
 * Service to manage and validate namespaces within tenants.
 */
public interface NamespaceService {

    /**
     * Checks whether a given namespace exists. A namespace is considered existing if at least one Flow is within the namespace or a parent namespace.
     *
     * @param tenant        The tenant ID.
     * @param namespace     The namespace - cannot be null.
     * @return  {@code true} if the namespace exists, {@code false} otherwise.
     */
    boolean isNamespaceExists(String tenant, String namespace);

    /**
     * Returns true if require existing namespace is enabled and the namespace didn't already exist.
     * <p>
     * WARNING: As namespace management is an EE feature, this will always return {@code false} in OSS.
     *
     * @param tenant        The tenant ID.
     * @param namespace     The namespace to check.
     * @return {@code true} if existing namespace is required and doesn't exist, {@code false} otherwise.
     */
    default boolean requireExistingNamespace(String tenant, String namespace) {
        return false;
    }

    /**
     * Returns true if the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * <p>
     * WARNING: As namespace management is an EE feature, this will always return {@code true} in OSS.
     *
     * @param tenant        The target tenant ID.
     * @param namespace     The target namespace to check.
     * @param fromTenant    The source tenant ID.
     * @param fromNamespace The source namespace.
     * @return {@code true} if the namespace is allowed, {@code false} otherwise.
     */
    default boolean isAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Checks that the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * If not, throws a ResourceAccessDeniedException.
     *
     * @param tenant        The target tenant ID.
     * @param namespace     The target namespace to check.
     * @param fromTenant    The source tenant ID.
     * @param fromNamespace The source namespace.
     * @throws ResourceAccessDeniedException if the namespace is not allowed.
     */
    void checkAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace);

    /**
     * Returns true if all namespaces are allowed from the namespace in the 'fromTenant' tenant.
     * <p>
     *  WARNING: As namespace management is an EE feature, this will always return {@code true} in OSS.
     *
     * @param tenant        The target tenant ID.
     * @param fromTenant    The source tenant ID.
     * @param fromNamespace The source namespace.
     * @return {@code true} if all namespaces are allowed, {@code false} otherwise.
     */
    default boolean areAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Checks that all namespaces are allowed from the namespace in the 'fromTenant' tenant.
     * If not, throws a ResourceAccessDeniedException.
     *
     * @param tenant        The target tenant ID.
     * @param fromTenant    The source tenant ID.
     * @param fromNamespace The source namespace.
     * @throws ResourceAccessDeniedException if all namespaces aren't allowed.
     */
    void checkAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace);
}
