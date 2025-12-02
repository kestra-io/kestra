package io.kestra.core.services;

import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
public class KVStoreService {
    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceService namespaceService;

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @param tenant        The tenant ID.
     * @param namespace     The namespace of the K/V store.
     * @param fromNamespace The namespace from which the K/V store is accessed.
     * @return The {@link KVStore}.
     */
    public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
        boolean isNotSameNamespace = fromNamespace != null && !namespace.equals(fromNamespace);
        if (isNotSameNamespace && isNotParentNamespace(namespace, fromNamespace)) {
            try {
                namespaceService.checkAllowedNamespace(tenant, namespace, tenant, fromNamespace);
            } catch (IllegalArgumentException e) {
                throw new KVStoreException(String.format(
                    "Cannot access the KV store. Access to '%s' namespace is not allowed from '%s'.", namespace, fromNamespace)
                );
            }
        }

        // Only check namespace existence if not a descendant
        boolean checkIfNamespaceExists = fromNamespace == null || isNotParentNamespace(namespace, fromNamespace);
        if (checkIfNamespaceExists && !namespaceService.isNamespaceExists(tenant, namespace)) {
            // if it didn't exist, we still check if there are KV as you can add KV without creating a namespace in DB or having flows in it
            KVStore kvStore = new InternalKVStore(tenant, namespace, storageInterface, kvMetadataRepository);
            try {
                if (kvStore.list(Pageable.from(1, 1)).isEmpty()) {
                    throw new KVStoreException(String.format(
                        "Cannot access the KV store. The namespace '%s' does not exist.",
                        namespace
                    ));
                }
            } catch (IOException e) {
                throw new KVStoreException(e);
            }
            return kvStore;
        }

        return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataRepository);
    }

    private static boolean isNotParentNamespace(final String parentNamespace, final String childNamespace) {
        return !childNamespace.startsWith(parentNamespace);
    }
}
