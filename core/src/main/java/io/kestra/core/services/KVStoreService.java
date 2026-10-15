package io.kestra.core.services;

import java.io.IOException;
import java.util.Optional;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVBackend;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.KVValue;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Gives access to the namespace Key-Value stores, from any server including a worker, once the access is allowed.
 * For server-side administration (paginated listing, purging), see {@link KVService}.
 */
@Singleton
public class KVStoreService {
    private final KVBackend backend;
    private final NamespaceService namespaceService;
    private final KVMetadataStateStore kvMetadataStateStore;

    @Inject
    public KVStoreService(KVBackend backend, NamespaceService namespaceService, KVMetadataStateStore kvMetadataStateStore) {
        this.backend = backend;
        this.namespaceService = namespaceService;
        this.kvMetadataStateStore = kvMetadataStateStore;
    }

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @return The {@link KVStore}.
     */
    public KVStore get(String tenant, String namespace) {
        return get(tenant, namespace, namespace);
    }

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @param fromNamespace The namespace from which the K/V store is accessed.
     * @return The {@link KVStore}.
     */
    public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
        checkAccessNamespaceIsAllowed(tenant, namespace, fromNamespace);
        return new InternalKVStore(tenant, namespace, backend);
    }

    /**
     * Finds the value of a key in the given namespace, then in each of its parent namespaces up to the root,
     * and returns the first one found.
     *
     * @throws ResourceExpiredException if the closest entry found for the key expired.
     */
    public Optional<KVValue> findValueWithInheritance(String tenant, String namespace, String key) throws IOException, ResourceExpiredException {
        String current = namespace;
        while (true) {
            Optional<KVValue> value = get(tenant, current, namespace).getValue(key);
            if (value.isPresent() || !current.contains(".")) {
                return value;
            }
            current = current.substring(0, current.lastIndexOf('.'));
        }
    }

    /**
     * Checks if access to the given namespace is allowed from the specified namespace and if the namespace exists.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @param fromNamespace The namespace from which the K/V store is accessed.
     */
    public void checkAccessNamespaceIsAllowed(String tenant, String namespace, @Nullable String fromNamespace) {
        // A namespace inherits the K/V store of its ancestors, so the allow-list only governs access to any other namespace.
        boolean inheritsTargetNamespace = fromNamespace != null && isDescendantOrSelf(namespace, fromNamespace);

        if (fromNamespace != null && !inheritsTargetNamespace) {
            try {
                namespaceService.checkAllowedNamespace(tenant, namespace, tenant, fromNamespace);
            } catch (ResourceAccessDeniedException e) {
                throw new KVStoreException(
                    String.format(
                        "Cannot access the KV store. Access to '%s' namespace is not allowed from '%s'.", namespace, fromNamespace
                    )
                );
            }
        }

        // Only check namespace existence if not a descendant
        if (!inheritsTargetNamespace && !namespaceService.isNamespaceExists(tenant, namespace)) {
            // if it didn't exist, we still check if there are KV as you can add KV without creating a namespace in DB or having flows in it
            if (!kvMetadataStateStore.existsByNamespace(tenant, namespace)) {
                throw new KVStoreException(
                    String.format(
                        "Cannot access the KV store. The namespace '%s' does not exist.",
                        namespace
                    )
                );
            }
        }
    }

    /**
     * The trailing dot is required: without it 'prod2' would be treated as a descendant of 'prod' and skip the allow-list.
     */
    private static boolean isDescendantOrSelf(final String parentNamespace, final String childNamespace) {
        return childNamespace.equals(parentNamespace) || childNamespace.startsWith(parentNamespace + ".");
    }
}
