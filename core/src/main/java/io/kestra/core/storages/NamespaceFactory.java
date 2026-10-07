package io.kestra.core.storages;

import org.slf4j.Logger;

import io.kestra.core.namespace.NamespaceFileMetadataStateStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating {@link Namespace} instances.
 */
@Singleton
public class NamespaceFactory {

    private final NamespaceFileMetadataStateStore namespaceFileMetadataStateStore;
    private final NamespaceFileBackend backend;

    @Inject
    public NamespaceFactory(NamespaceFileMetadataStateStore namespaceFileMetadataStateStore, NamespaceFileBackend backend) {
        this.namespaceFileMetadataStateStore = namespaceFileMetadataStateStore;
        this.backend = backend;
    }

    public Namespace of(String tenantId, String namespace) {
        return new InternalNamespace(tenantId, namespace, backend, namespaceFileMetadataStateStore);
    }

    public Namespace of(Logger logger, String tenantId, String namespace) {
        return new InternalNamespace(logger, tenantId, namespace, backend, namespaceFileMetadataStateStore);
    }
}
