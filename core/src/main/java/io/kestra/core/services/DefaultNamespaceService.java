package io.kestra.core.services;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
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

    private final Optional<FlowRepositoryInterface> flowRepository;

    @Inject
    public DefaultNamespaceService(Optional<FlowRepositoryInterface> flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        return flowRepository.map(repository -> repository.isNamespaceExists(tenant, namespace)).orElse(false);
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
