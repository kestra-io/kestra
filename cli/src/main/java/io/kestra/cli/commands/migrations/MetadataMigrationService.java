package io.kestra.cli.commands.migrations;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class MetadataMigrationService {
    @Inject
    private TenantService tenantService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    private StorageInterface storageInterface;

    protected Map<String, List<String>> namespacesPerTenant() {
        String tenantId = tenantService.resolveTenant();
        return Map.of(tenantId, flowRepository.findDistinctNamespace(tenantId));
    }

    public int migrateMetadata() {
        try {
            kvMigration();
        } catch (IOException e) {
            System.err.println("❌ KV metadata migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }

        return 0;
    }

    private void kvMigration() throws IOException {
        this.namespacesPerTenant().entrySet().stream()
            .flatMap(namespacesForTenant -> namespacesForTenant.getValue().stream().map(namespace -> Map.entry(namespacesForTenant.getKey(), namespace)))
            .flatMap(throwFunction(namespaceForTenant -> {
                InternalKVStore kvStore = new InternalKVStore(namespaceForTenant.getKey(), namespaceForTenant.getValue(), storageInterface, kvMetadataRepository);
                List<FileAttributes> list = listAllFromStorage(storageInterface, namespaceForTenant.getKey(), namespaceForTenant.getValue());
                Map<Boolean, List<KVEntry>> entriesByIsExpired = list.stream()
                    .map(throwFunction(fileAttributes -> KVEntry.from(namespaceForTenant.getValue(), fileAttributes)))
                    .collect(Collectors.partitioningBy(kvEntry -> Optional.ofNullable(kvEntry.expirationDate()).map(expirationDate -> Instant.now().isAfter(expirationDate)).orElse(false)));

                entriesByIsExpired.get(true).forEach(kvEntry -> {
                    try {
                        storageInterface.delete(
                            namespaceForTenant.getKey(),
                            namespaceForTenant.getValue(),
                            kvStore.storageUri(kvEntry.key())
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                return entriesByIsExpired.get(false).stream().map(kvEntry -> PersistedKvMetadata.from(namespaceForTenant.getKey(), kvEntry));
            }))
            .forEach(kvMetadataRepository::save);
    }

    private static List<FileAttributes> listAllFromStorage(StorageInterface storage, String tenant, String namespace) throws IOException {
        try {
            return storage.list(tenant, namespace, URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace)));
        } catch (FileNotFoundException e) {
            return Collections.emptyList();
        }
    }
}
