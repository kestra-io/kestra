package io.kestra.cli.commands.migrations.metadata;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwConsumer;
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
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;

    @Inject
    private StorageInterface storageInterface;

    protected Map<String, List<String>> namespacesPerTenant() {
        String tenantId = tenantService.resolveTenant();
        return Map.of(tenantId, flowRepository.findDistinctNamespace(tenantId));
    }

    public void kvMigration() throws IOException {
        this.namespacesPerTenant().entrySet().stream()
            .flatMap(namespacesForTenant -> namespacesForTenant.getValue().stream().map(namespace -> Map.entry(namespacesForTenant.getKey(), namespace)))
            .flatMap(throwFunction(namespaceForTenant -> {
                InternalKVStore kvStore = new InternalKVStore(namespaceForTenant.getKey(), namespaceForTenant.getValue(), storageInterface, kvMetadataRepository);
                List<FileAttributes> list = listAllFromStorage(storageInterface, StorageContext::kvPrefix, namespaceForTenant.getKey(), namespaceForTenant.getValue()).stream()
                    .map(PathAndAttributes::attributes)
                    .toList();
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
            .forEach(throwConsumer(kvMetadata -> {
                if (kvMetadataRepository.findByName(kvMetadata.getTenantId(), kvMetadata.getNamespace(), kvMetadata.getName()).isEmpty()) {
                    kvMetadataRepository.save(kvMetadata);
                }
            }));
    }

    public void nsFilesMigration() throws IOException {
        this.namespacesPerTenant().entrySet().stream()
            .flatMap(namespacesForTenant -> namespacesForTenant.getValue().stream().map(namespace -> Map.entry(namespacesForTenant.getKey(), namespace)))
            .flatMap(throwFunction(namespaceForTenant -> {
                List<PathAndAttributes> list = listAllFromStorage(storageInterface, StorageContext::namespaceFilePrefix, namespaceForTenant.getKey(), namespaceForTenant.getValue());
                return list.stream()
                    .map(pathAndAttributes -> NamespaceFileMetadata.of(namespaceForTenant.getKey(), namespaceForTenant.getValue(), pathAndAttributes.path(), pathAndAttributes.attributes()));
            }))
            .forEach(throwConsumer(nsFileMetadata -> {
                if (namespaceFileMetadataRepository.findByPath(nsFileMetadata.getTenantId(), nsFileMetadata.getNamespace(), nsFileMetadata.getPath()).isEmpty()) {
                    namespaceFileMetadataRepository.save(nsFileMetadata);
                }
            }));
    }

    public void secretMigration() throws Exception {
        throw new UnsupportedOperationException("Secret migration is not needed in the OSS version");
    }

    private static List<PathAndAttributes> listAllFromStorage(StorageInterface storage, Function<String, String> prefixFunction, String tenant, String namespace) throws IOException {
        try {
            String prefix = prefixFunction.apply(namespace);
            return storage.allByPrefix(tenant, namespace, URI.create(StorageContext.KESTRA_PROTOCOL + prefix + "/"), true).stream()
                .map(throwFunction(uri -> new PathAndAttributes(uri.getPath().substring(prefix.length()), storage.getAttributes(tenant, namespace, uri))))
                .toList();
        } catch (FileNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public record PathAndAttributes(String path, FileAttributes attributes) {}
}
