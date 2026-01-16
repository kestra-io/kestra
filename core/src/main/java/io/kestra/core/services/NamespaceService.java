package io.kestra.core.services;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.NamespaceUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class NamespaceService {

    private final Optional<FlowRepositoryInterface> flowRepository;

    @Inject
    public NamespaceService(Optional<FlowRepositoryInterface> flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * Checks whether a given namespace exists. A namespace is considered existing if at least one Flow is within the namespace or a parent namespace
     *
     * @param tenant        The tenant ID
     * @param namespace     The namespace - cannot be null.
     * @return  {@code true} if the namespace exist. Otherwise {@link false}.
     */
    public boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");

        if (flowRepository.isPresent()) {
            List<String> namespaces = flowRepository.get().findDistinctNamespace(tenant).stream()
                .map(NamespaceUtils::asTree)
                .flatMap(Collection::stream)
                .toList();
            return namespaces.stream().anyMatch(ns -> ns.equals(namespace) || ns.startsWith(namespace));
        }
        return false;
    }

    /**
     * Return true if require existing namespace is enabled and the namespace didn't already exist.
     * As namespace management is an EE feature, this will always return false in OSS.
     */
    public boolean requireExistingNamespace(String tenant, String namespace) {
        return false;
    }

    /**
     * Return true if the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * As namespace restriction is an EE feature, this will always return true in OSS.
     */
    public boolean isAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Check that the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * If not, throw a ResourceAccessDeniedException.
     *
     * @throws ResourceAccessDeniedException if the namespace is not allowed.
     */
    public void checkAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        if (!isAllowedNamespace(tenant, namespace, fromTenant, fromNamespace)) {
            throw new ResourceAccessDeniedException("Namespace " + namespace + " is not allowed.");
        }
    }

    /**
     * Return true if the namespace is allowed from all the namespace in the 'fromTenant' tenant.
     * As namespace restriction is an EE feature, this will always return true in OSS.
     */
    public boolean areAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Check that the namespace is allowed from all the namespace in the 'fromTenant' tenant.
     * If not, throw a ResourceAccessDeniedException.
     *
     * @throws ResourceAccessDeniedException if all namespaces all aren't allowed.
     */
    public void checkAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        if (!areAllowedAllNamespaces(tenant, fromTenant, fromNamespace)) {
            throw new ResourceAccessDeniedException("All namespaces are not allowed, you should either filter on a namespace or configure all namespaces to allow your namespace.");
        }
    }
}
