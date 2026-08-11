package io.kestra.core.services;

import java.util.Objects;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.runners.FlowMetaStoreInterface;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link NamespaceService}.
 */
@Singleton
public class DefaultNamespaceService implements NamespaceService {

    private final Provider<FlowMetaStoreInterface> flowMetaStore;

    @Inject
    public DefaultNamespaceService(Provider<FlowMetaStoreInterface> flowMetaStore) {
        this.flowMetaStore = flowMetaStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        return flowMetaStore.get().isNamespaceExists(tenant, namespace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        if (!isAllowedNamespace(tenant, namespace, fromTenant, fromNamespace)) {
            throw new ResourceAccessDeniedException("Namespace " + namespace + " is not allowed.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        if (!areAllowedAllNamespaces(tenant, fromTenant, fromNamespace)) {
            throw new ResourceAccessDeniedException("All namespaces are not allowed, you should either filter on a namespace or configure all namespaces to allow your namespace.");
        }
    }
}
