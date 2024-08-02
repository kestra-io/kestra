package io.kestra.core.services;

import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

@Singleton
public class NamespaceService {

    private final FlowRepositoryInterface flowRepository;

    @Inject
    public NamespaceService(FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * Checks whether a given namespace exists.
     *
     * @param tenant        The tenant ID
     * @param namespace     The namespace - cannot be null.
     * @return  {@code true} if the namespace exist. Otherwise {@link false}.
     */
    public boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");

        List<String> namespaces =  flowRepository.findDistinctNamespace(tenant);
        return namespaces.stream().anyMatch(ns -> ns.equals(namespace));
    }

}
