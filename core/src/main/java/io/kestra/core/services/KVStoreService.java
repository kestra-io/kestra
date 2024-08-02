package io.kestra.core.services;

import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KVStoreService {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowService flowService;

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

        boolean checkIfNamespaceExists = fromNamespace == null || !namespace.startsWith(fromNamespace);

        if (checkIfNamespaceExists && !namespaceService.isNamespaceExists(tenant, namespace)) {
            throw new KVStoreException(String.format(
                "Cannot access the KV store. The namespace '%s' does not exist.",
                namespace
            ));
        }

        boolean isNotSameNamespace = fromNamespace != null && !namespace.equals(fromNamespace);

        if (isNotSameNamespace && !namespace.startsWith(fromNamespace)) {
            throw new KVStoreException(String.format(
                "Cannot access the KV store. The '%s' namespace is neither equal to nor a descendant of '%s'",
                namespace,
                fromNamespace
            ));
        }

        if (isNotSameNamespace) {
            try {
                flowService.checkAllowedNamespace(tenant, namespace, tenant, fromNamespace);
            } catch (IllegalArgumentException e) {
                throw new KVStoreException(String.format(
                    "Cannot access the KV store. Access to '%s' namespace is not allowed from '%s'.", namespace, fromNamespace)
                );
            }
        }

        return new InternalKVStore(tenant, namespace, storageInterface);
    }
}
