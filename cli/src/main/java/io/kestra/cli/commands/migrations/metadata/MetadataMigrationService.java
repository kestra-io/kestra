package io.kestra.cli.commands.migrations.metadata;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.contexts.KestraConfig;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.models.namespaces.NamespaceInterface;
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

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@Singleton
public class MetadataMigrationService {
    // Captures the logical path (group 1) and the revision number (group 2) of a stored namespace
    // file. Version 1 is stored under the bare path (no suffix); later versions use a ".vN" suffix
    // where N >= 2. A ".v1" suffix is therefore invalid: it would collide with the bare path and
    // corrupt the version chain if both existed simultaneously.
    private static final Pattern REVISION_FILE_PATTERN = Pattern.compile("(.*)\\.v(\\d+)$");

    protected FlowRepositoryInterface flowRepository;
    protected TenantService tenantService;
    protected KvMetadataRepositoryInterface kvMetadataRepository;
    protected NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;
    protected StorageInterface storageInterface;
    protected KestraConfig kestraConfig;

    @Singleton
    public MetadataMigrationService(FlowRepositoryInterface flowRepository,
        TenantService tenantService,
        KvMetadataRepositoryInterface kvMetadataRepository,
        NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository,
        StorageInterface storageInterface,
        KestraConfig kestraConfig) {
        this.flowRepository = flowRepository;
        this.tenantService = tenantService;
        this.kvMetadataRepository = kvMetadataRepository;
        this.namespaceFileMetadataRepository = namespaceFileMetadataRepository;
        this.storageInterface = storageInterface;
        this.kestraConfig = kestraConfig;
    }

    @VisibleForTesting
    public Map<String, List<String>> namespacesPerTenant() {
        String tenantId = tenantService.resolveTenant();
        return Map.of(
            tenantId, Stream.concat(
                Stream.of(kestraConfig.getSystemFlowNamespace()),
                flowRepository.findDistinctNamespace(tenantId).stream()
            ).map(NamespaceInterface::asTree).flatMap(Collection::stream).distinct().toList()
        );
    }

    public void kvMigration(String tenantFilter) throws IOException {
        filterTenants(this.namespacesPerTenant(), tenantFilter).entrySet().stream()
            .flatMap(namespacesForTenant -> namespacesForTenant.getValue().stream().map(namespace -> Map.entry(namespacesForTenant.getKey(), namespace)))
            .flatMap(throwFunction(namespaceForTenant ->
            {
                InternalKVStore kvStore = new InternalKVStore(namespaceForTenant.getKey(), namespaceForTenant.getValue(), storageInterface, kvMetadataRepository);
                List<FileAttributes> list = listAllFromStorage(storageInterface, StorageContext::kvPrefix, namespaceForTenant.getKey(), namespaceForTenant.getValue()).stream()
                    .map(PathAndAttributes::attributes)
                    .toList();
                Map<Boolean, List<KVEntry>> entriesByIsExpired = list.stream()
                    .map(throwFunction(fileAttributes -> KVEntry.from(namespaceForTenant.getValue(), fileAttributes)))
                    .collect(Collectors.partitioningBy(kvEntry -> Optional.ofNullable(kvEntry.expirationDate()).map(expirationDate -> Instant.now().isAfter(expirationDate)).orElse(false)));

                entriesByIsExpired.get(true).forEach(kvEntry ->
                {
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
            .forEach(throwConsumer(kvMetadata ->
            {
                if (kvMetadataRepository.findByName(kvMetadata.getTenantId(), kvMetadata.getNamespace(), kvMetadata.getName()).isEmpty()) {
                    kvMetadataRepository.save(kvMetadata);
                }
            }));
    }

    public void nsFilesMigration(boolean verbose, String tenantFilter) throws IOException {
        for (var tenantNamespaces : filterTenants(this.namespacesPerTenant(), tenantFilter).entrySet()) {
            var tenant = tenantNamespaces.getKey();
            for (var namespace : tenantNamespaces.getValue()) {
                var list = listAllFromStorage(storageInterface, StorageContext::namespaceFilePrefix, tenant, namespace);

                // Group every stored object (the bare file plus its ".vN" revisions) by logical path
                // so the full version history is reconstructed instead of keeping only the bare file.
                var revisionsByPath = list.stream()
                    .collect(Collectors.groupingBy(pathAndAttributes -> logicalPath(pathAndAttributes.path())));

                for (var revisions : revisionsByPath.entrySet()) {
                    var path = revisions.getKey();

                    // Highest version already indexed (0 if the path was never migrated). A previously
                    // broken run only indexed the bare file as v1, so the higher ".vN" revisions still
                    // need backfilling; replaying only revisions above this threshold keeps the
                    // migration idempotent while repairing partially-migrated paths.
                    var alreadyMigratedVersions = namespaceFileMetadataRepository.findByPath(tenant, namespace, path)
                        .map(NamespaceFileMetadata::getVersion)
                        .orElse(0);

                    // Compute revision numbers once per element so the sort key, filter, and log
                    // all share the same value without repeated Matcher allocations.
                    var withRevision = revisions.getValue().stream()
                        .map(paa -> Map.entry(paa, revisionNumber(tenant, namespace, paa.path())))
                        .toList();

                    // Replay the missing revisions from oldest to newest: save() increments the version
                    // and flags the latest one as "last", mirroring how files are normally versioned.
                    withRevision.stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getValue))
                        .filter(entry -> entry.getValue() > alreadyMigratedVersions)
                        .forEach(entry ->
                        {
                            try {
                                var saved = namespaceFileMetadataRepository.save(
                                    NamespaceFileMetadata.of(tenant, namespace, path, entry.getKey().attributes())
                                );
                                if (verbose) {
                                    System.out.println("Migrated namespace file metadata: " + namespace + " - " + path + " (v" + saved.getVersion() + ")");
                                }
                            } catch (Exception e) {
                                log.error(
                                    "Failed to migrate namespace file metadata for tenant='{}' namespace='{}' path='{}' storageRevision={} — migration aborted at this entry",
                                    tenant, namespace, path, entry.getValue(), e
                                );
                                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                            }
                        });
                }
            }
        }
    }

    private static String logicalPath(String path) {
        Matcher matcher = REVISION_FILE_PATTERN.matcher(path);
        return matcher.matches() ? matcher.group(1) : path;
    }

    // Returns the storage revision for a given object path. Bare paths (no ".vN" suffix) are
    // revision 1. A ".v1" suffix is invalid: it would collide with the bare path and produce two
    // objects with revision 1, silently overwriting the first on save(). Fail loudly to prevent
    // silent corruption; the operator must inspect and remove the offending object manually.
    private static int revisionNumber(String tenant, String namespace, String path) {
        var matcher = REVISION_FILE_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return 1;
        }
        var revision = Integer.parseInt(matcher.group(2));
        if (revision == 1) {
            throw new IllegalStateException(
                "Storage object with '.v1' suffix found — this collides with the bare path and would corrupt the version chain. " +
                "Tenant='" + tenant + "' namespace='" + namespace + "' path='" + path + "'. " +
                "Remove or rename the offending object and re-run the migration."
            );
        }
        return revision;
    }

    public void secretMigration(String tenantFilter) throws Exception {
        throw new UnsupportedOperationException("Secret migration is not needed in the OSS version");
    }

    protected static Map<String, List<String>> filterTenants(Map<String, List<String>> namespacesPerTenant, String tenantFilter) {
        if (tenantFilter == null) {
            return namespacesPerTenant;
        }
        if (!namespacesPerTenant.containsKey(tenantFilter)) {
            throw new IllegalArgumentException("Tenant '" + tenantFilter + "' not found. Available tenants: " + namespacesPerTenant.keySet());
        }
        return Map.of(tenantFilter, namespacesPerTenant.get(tenantFilter));
    }

    private static List<PathAndAttributes> listAllFromStorage(StorageInterface storage, Function<String, String> prefixFunction, String tenant, String namespace) throws IOException {
        try {
            String prefix = prefixFunction.apply(namespace);

            return storage.allByPrefix(tenant, namespace, URI.create(StorageContext.KESTRA_PROTOCOL + prefix + "/"), true).stream()
                .map(throwFunction(uri -> new PathAndAttributes(uri.getPath().substring(prefix.length()), storage.getAttributes(tenant, namespace, uri))))
                .toList();
        } catch (FileNotFoundException | NoSuchFileException e) {
            return Collections.emptyList();
        }
    }

    public record PathAndAttributes(String path, FileAttributes attributes) {
    }
}
