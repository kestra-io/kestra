package io.kestra.core.services;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowMetaStoreInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link NamespaceService}.
 */
@Singleton
public class DefaultNamespaceService implements NamespaceService {

    private final FlowMetaStoreInterface flowMetaStore;

    @Inject
    public DefaultNamespaceService(FlowMetaStoreInterface flowMetaStore) {
        this.flowMetaStore = flowMetaStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        return flowMetaStore.isNamespaceExists(tenant, namespace);
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
