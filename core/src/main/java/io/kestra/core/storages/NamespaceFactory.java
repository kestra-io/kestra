package io.kestra.core.storages;

import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

/**
 * Factory for creating {@link Namespace} instances.
 */
@Singleton
public class NamespaceFactory {

    private final NamespaceFileMetadataStateStore namespaceFileMetadataStateStore;

    @Inject
    public NamespaceFactory(NamespaceFileMetadataStateStore namespaceFileMetadataStateStore) {
        this.namespaceFileMetadataStateStore = namespaceFileMetadataStateStore;
    }

    public Namespace of(String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(tenantId, namespace, storageInterface, namespaceFileMetadataStateStore);
    }

    public Namespace of(Logger logger, String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(logger, tenantId, namespace, storageInterface, namespaceFileMetadataStateStore);
    }
}
