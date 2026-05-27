package io.kestra.core.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Storage-layer maintenance for orphaned execution files. Mirrors {@link ExecutionLogService} for logs: walks
 * storage without consulting any repository, so it can reclaim files of flows that no longer exist — including the
 * post-isolated-worker-group case in <a href="https://github.com/kestra-io/kestra-ee/issues/6699">kestra-ee#6699</a>.
 */
@Singleton
@Slf4j
public class StoragePurgeService {
    private final StorageInterface storageInterface;

    @Inject
    public StoragePurgeService(StorageInterface storageInterface) {
        this.storageInterface = storageInterface;
    }

    /**
     * Purges {@code executions/{id}/} subtrees whose newest contained file falls within {@code [startDate, endDate]}.
     * Namespace matching is exact: {@code namespace=a.b} does not reach {@code a.b.c} (purge that explicitly).
     *
     * @param tenantId  tenant identifier; {@code null} only meaningful for backends that don't enforce tenancy
     * @param namespace exact namespace; {@code null} = every namespace under the tenant
     * @param flowId    restrict to a single flow (requires {@code namespace})
     * @param startDate lower bound on newest-file mtime (nullable)
     * @param endDate   upper bound on newest-file mtime; also the in-flight guard (nullable)
     * @param dryRun    when {@code true}, match but delete nothing
     */
    public StoragePurgeResult purgeByLastModified(
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean dryRun
    ) throws IOException {
        if (flowId != null && namespace == null) {
            throw new IllegalArgumentException("'namespace' is required when 'flowId' is set.");
        }

        Long startMillis = startDate == null ? null : startDate.toInstant().toEpochMilli();
        Long endMillis = endDate == null ? null : endDate.toInstant().toEpochMilli();

        int scanned = 0;
        int deletedFiles = 0;
        List<URI> purgedUris = new ArrayList<>();

        for (URI executionUri : findExecutionDirs(tenantId, namespace, flowId)) {
            scanned++;
            try {
                // Use newest contained file, not the dir's own timestamp: directory timestamps are virtual on object
                // stores and don't advance on nested writes on a local fs. Also naturally preserves in-flight runs.
                OptionalLong lastModified = lastModifiedFileTime(tenantId, executionUri);
                if (lastModified.isEmpty()
                    || (startMillis != null && lastModified.getAsLong() < startMillis)
                    || (endMillis != null && lastModified.getAsLong() > endMillis)) {
                    continue;
                }

                if (!dryRun) {
                    // Delete first, record after — failed deletes don't end up in purgedUris.
                    deletedFiles += storageInterface.deleteByPrefix(tenantId, null, executionUri).size();
                }
                purgedUris.add(executionUri);
            } catch (Exception e) {
                // Per-execution isolation: one bad subtree must not abort the rest.
                log.warn("Failed to purge storage at '{}'; skipping it.", executionUri, e);
            }
        }

        return new StoragePurgeResult(scanned, deletedFiles, List.copyOf(purgedUris));
    }

    /**
     * Resolves the {@code executions/{id}/} directories to scan by walking storage directly:
     * {@code namespace+flowId} → one flow's executions; {@code namespace} only → its direct flow dirs (exact match,
     * no sub-namespace recursion); neither → recursive tenant-wide walk (gated by {@code allowAllNamespaces}).
     */
    private List<URI> findExecutionDirs(@Nullable String tenantId, @Nullable String namespace, @Nullable String flowId) throws IOException {
        List<URI> result = new ArrayList<>();
        if (namespace != null && flowId != null) {
            addChildDirs(tenantId, StorageContext.executionsRootUri(namespace, flowId), result);
            return result;
        }
        URI scanRoot = namespace == null
            ? URI.create(StorageContext.KESTRA_PROTOCOL + "/")
            : StorageContext.namespaceRootUri(namespace);
        if (namespace != null) {
            for (FileAttributes flowCandidate : listAsAdminOrEmpty(tenantId, scanRoot)) {
                if (!isFlowCandidate(flowCandidate) || isAmbiguousFlowName(flowCandidate.getFileName(), namespace)) {
                    continue;
                }
                addChildDirs(tenantId, resolveChildDir(scanRoot, flowCandidate.getFileName() + "/" + StorageContext.EXECUTIONS_DIR_NAME), result);
            }
            return result;
        }
        collectExecutionDirs(tenantId, scanRoot, result);
        return result;
    }

    /**
     * Recursive tenant-wide walk. At each level, probes {@code <child>/executions/} to distinguish flow dir (scan its
     * executions/) from namespace level (recurse). Probing — instead of matching the literal name {@code "executions"}
     * — avoids treating a sub-namespace segment or flow named {@code executions} as the executions terminal.
     */
    private void collectExecutionDirs(@Nullable String tenantId, URI dirUri, List<URI> out) throws IOException {
        for (FileAttributes child : listAsAdminOrEmpty(tenantId, dirUri)) {
            if (!isFlowCandidate(child)) {
                continue;
            }
            URI childUri = resolveChildDir(dirUri, child.getFileName());
            URI executionsSubdir = resolveChildDir(childUri, StorageContext.EXECUTIONS_DIR_NAME);
            List<FileAttributes> executionDirs = listAsAdminOrEmpty(tenantId, executionsSubdir);
            // Ambiguous-name case (child literally "executions"): recurse rather than skip — skipping would silently
            // orphan a legitimate sub-namespace's flows under it, while recursing still reaches them at a deeper
            // level where the parent-name ambiguity no longer applies.
            if (executionDirs.isEmpty() || isAmbiguousFlowName(child.getFileName(), null)) {
                collectExecutionDirs(tenantId, childUri, out);
            } else {
                for (FileAttributes executionDir : executionDirs) {
                    if (executionDir.getType() == FileAttributes.FileType.Directory) {
                        out.add(resolveChild(executionsSubdir, executionDir.getFileName()));
                    }
                }
            }
        }
    }

    /** Adds direct subdirectories of {@code dirUri} to {@code out} as slash-less URIs (deleteByPrefix-shaped). */
    private void addChildDirs(@Nullable String tenantId, URI dirUri, List<URI> out) throws IOException {
        for (FileAttributes child : listAsAdminOrEmpty(tenantId, dirUri)) {
            if (child.getType() == FileAttributes.FileType.Directory) {
                out.add(resolveChild(dirUri, child.getFileName()));
            }
        }
    }

    /**
     * Newest file mtime under {@code prefix}, or empty if no files. Listing errors are isolated via
     * {@link #listAsAdminOrEmpty}, so a transient failure deep in a subtree yields "no files found" rather than
     * aborting the whole recursion.
     */
    private OptionalLong lastModifiedFileTime(@Nullable String tenantId, URI prefix) {
        long max = Long.MIN_VALUE;
        boolean found = false;

        for (FileAttributes child : listAsAdminOrEmpty(tenantId, prefix)) {
            if (child.getType() == FileAttributes.FileType.Directory) {
                OptionalLong sub = lastModifiedFileTime(tenantId, resolveChild(prefix, child.getFileName()));
                if (sub.isPresent()) {
                    max = Math.max(max, sub.getAsLong());
                    found = true;
                }
            } else {
                max = Math.max(max, child.getLastModifiedTime());
                found = true;
            }
        }

        return found ? OptionalLong.of(max) : OptionalLong.empty();
    }

    private static boolean isFlowCandidate(FileAttributes attrs) {
        return attrs.getType() == FileAttributes.FileType.Directory
            && !attrs.getFileName().startsWith(StorageContext.RESERVED_NAMESPACE_DIR_PREFIX);
    }

    /**
     * True when a child name collides with the {@code executions/} path segment. Storage cannot distinguish a flow
     * named {@code executions} from a sub-namespace whose last segment is {@code executions}, so namespace-scoped
     * scans skip the ambiguous name and require explicit {@code flowId} to act on it.
     */
    private boolean isAmbiguousFlowName(String childName, @Nullable String namespace) {
        if (!StorageContext.EXECUTIONS_DIR_NAME.equals(childName)) {
            return false;
        }
        log.warn("Skipping ambiguous flow/sub-namespace path segment '{}' under namespace '{}'; target the flow explicitly via 'flowId' to purge it.",
            childName, namespace);
        return true;
    }

    private static URI resolveChild(URI parent, String name) {
        String base = parent.toString();
        return URI.create(base.endsWith("/") ? base + name : base + "/" + name);
    }

    private static URI resolveChildDir(URI parent, String name) {
        return URI.create(resolveChild(parent, name) + "/");
    }

    /**
     * Lists {@code uri} returning empty on missing paths and on any I/O error (logged at WARN). Passes {@code null}
     * as the namespace to the backend: the task already validated ACL on the target namespace at entry, and threading
     * the namespace through here would only shrink results on EE backends that filter list output by namespace.
     * <p>
     * <strong>Must remain {@code private}.</strong> This is an admin-grade listing primitive whose safety depends on
     * the caller having performed the ACL check at the entry point. Exposing it package-private or higher would let a
     * caller that hasn't validated namespace access enumerate any tenant's storage, silently bypassing namespace
     * isolation.
     */
    private List<FileAttributes> listAsAdminOrEmpty(@Nullable String tenantId, URI uri) {
        try {
            return storageInterface.list(tenantId, null, uri);
        } catch (FileNotFoundException | NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            log.warn("Failed to list storage at '{}'; skipping this subtree.", uri, e);
            return List.of();
        }
    }

    /**
     * Result of a {@link #purgeByLastModified} run. Mirrors {@link ExecutionLogService.PurgeResult}.
     */
    public record StoragePurgeResult(int scannedCount, int deletedFilesCount, List<URI> purgedUris) {
        public StoragePurgeResult {
            // Tolerate null (e.g. partial Jackson payload) since List.copyOf(null) would NPE.
            purgedUris = purgedUris == null ? List.of() : List.copyOf(purgedUris);
        }

        public int purgedCount() {
            return purgedUris.size();
        }
    }
}
