package io.kestra.core.storages;

import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
public class NamespaceFactory {
    @Inject
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepositoryInterface;

    public Namespace of(String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(tenantId, namespace, storageInterface, namespaceFileMetadataRepositoryInterface);
    }

    public Namespace of(Logger logger, String tenantId, String namespace, StorageInterface storageInterface) {
        return new InternalNamespace(logger, tenantId, namespace, storageInterface, namespaceFileMetadataRepositoryInterface);
    }
}
