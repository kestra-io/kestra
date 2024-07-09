package io.kestra.core.services;

import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KVStoreService {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowService flowService;

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @return The {@link KVStore}.
     */
    public KVStore namespaceKv(String tenant, String namespace, String fromNamespace) {
        if (fromNamespace != null && !fromNamespace.equals(namespace)) {
            flowService.checkAllowedNamespace(tenant, namespace, tenant, fromNamespace);
        }

        return new InternalKVStore(tenant, namespace, storageInterface);
    }
}
