package io.kestra.core.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintenance service for orphaned execution storage. Resolves which
 * {@code <ns>/<flow>/executions/} prefixes are in scope, then delegates per-file
 * date filtering and deletion to {@link StorageInterface#purgeByLastModified}.
 * <p>
 * Mirrors {@link ExecutionLogService} for logs: walks storage directly (no flow
 * repository) so it can reclaim files of flows that no longer exist, including
 * the post-isolated-worker-group case in
 * <a href="https://github.com/kestra-io/kestra-ee/issues/6699">kestra-ee#6699</a>.
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
     * Purges execution storage whose files fall within {@code [startDate, endDate]}.
     * Namespace matching is recursive: {@code namespace=a.b} also reaches {@code a.b.c}.
     * <p>
     * Sub-namespaces are discovered by walking the parent's storage subtree, so recursion only reaches
     * children that share the parent's backend. A sub-namespace backed by its own dedicated storage is
     * physically absent from the parent subtree and is therefore not discovered — target it explicitly via
     * {@code namespace}. This walk-based discovery is deliberate: it lets the purge reclaim files of flows
     * (and whole namespaces) that no longer exist, which a flow-metadata enumeration could not.
     *
     * @param tenantId  tenant identifier
     * @param namespace namespace and all its sub-namespaces sharing its backend; {@code null} = walk every namespace under the tenant
     * @param flowId    restrict to a single flow (requires {@code namespace})
     * @param startDate inclusive lower bound on file mtime
     * @param endDate   inclusive upper bound on file mtime (acts as the in-flight guard)
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

        Instant startInstant = startDate == null ? null : startDate.toInstant();
        Instant endInstant = endDate == null ? null : endDate.toInstant();

        Aggregator agg = new Aggregator();
        if (namespace != null && flowId != null) {
            purgeFlow(tenantId, namespace, flowId, startInstant, endInstant, dryRun, agg);
        } else if (namespace != null) {
            walkNamespaceTree(tenantId, namespace, StorageContext.namespaceRootUri(namespace),
                startInstant, endInstant, dryRun, agg);
        } else {
            walkNamespaceTree(tenantId, "", URI.create(StorageContext.KESTRA_PROTOCOL + "/"),
                startInstant, endInstant, dryRun, agg);
        }

        return new StoragePurgeResult(agg.scanned, agg.purgedExecutions.size(), agg.deletedFiles);
    }

    /**
     * Purges a single flow's executions in one storage call. The shallow listing of {@code executions/}
     * before the purge call feeds the per-execution {@code scanned} counter so the caller can tell how
     * much work was looked at vs deleted; backends that override {@link StorageInterface#purgeByLastModified}
     * with a native primitive still benefit because the file-level walk happens inside the override.
     */
    private void purgeFlow(@Nullable String tenantId, String namespace, String flowId,
        @Nullable Instant startDate, @Nullable Instant endDate, boolean dryRun, Aggregator agg) throws IOException {
        URI executionsPrefix = StorageContext.executionsRootUri(namespace, flowId);

        for (FileAttributes child : safeList(tenantId, namespace, executionsPrefix)) {
            if (child.getType() == FileAttributes.FileType.Directory) {
                agg.scanned++;
            }
        }

        List<URI> matched = storageInterface.purgeByLastModified(
            tenantId, namespace, executionsPrefix, startDate, endDate, dryRun);
        for (URI uri : matched) {
            StorageContext.extractExecutionId(uri).ifPresent(agg.purgedExecutions::add);
        }
        if (!dryRun) {
            agg.deletedFiles += matched.size();
        }
    }

    /**
     * Recursive walk over a namespace subtree (or the whole tenant when {@code currentNamespace} is empty).
     * A {@code namespace}-scoped purge reaches every sub-namespace beneath it; e.g. {@code namespace=a.b} also
     * purges {@code a.b.c}. As we descend, we accumulate the dotted namespace from path segments and
     * thread it through {@link #safeList} so backends enforcing per-namespace isolation receive a valid
     * scope; only the tenant-root listing itself uses {@code null} (no namespace candidate exists yet).
     * <p>
     * At each level we probe {@code <child>/executions/} to distinguish a flow dir (purge it) from a
     * namespace segment (recurse). A directory literally named {@code executions} is ambiguous — could be
     * a flow named "executions" or a sub-namespace named "executions" — so we recurse rather than purge
     * to avoid orphaning legitimate sub-namespace flows; explicit {@code flowId} is needed to purge that.
     */
    private void walkNamespaceTree(@Nullable String tenantId, String currentNamespace, URI dirUri,
        @Nullable Instant startDate, @Nullable Instant endDate, boolean dryRun, Aggregator agg) throws IOException {
        String namespaceForList = currentNamespace.isEmpty() ? null : currentNamespace;
        for (FileAttributes child : safeList(tenantId, namespaceForList, dirUri)) {
            if (!isFlowCandidate(child)) {
                continue;
            }
            String childName = child.getFileName();
            URI childUri = URI.create(dirUri + childName + "/");
            URI executionsSubdir = URI.create(childUri + StorageContext.EXECUTIONS_DIR_NAME + "/");
            List<FileAttributes> executionDirs = safeList(tenantId, namespaceForList, executionsSubdir);

            if (executionDirs.isEmpty() || isAmbiguousFlowName(childName, null)) {
                String nextNamespace = currentNamespace.isEmpty() ? childName : currentNamespace + "." + childName;
                walkNamespaceTree(tenantId, nextNamespace, childUri, startDate, endDate, dryRun, agg);
            } else if (!currentNamespace.isEmpty()) {
                // Kestra always stores flows under a namespace, so a flow-shaped dir at tenant root is malformed.
                purgeFlow(tenantId, currentNamespace, childName, startDate, endDate, dryRun, agg);
            }
        }
    }

    private static boolean isFlowCandidate(FileAttributes attrs) {
        return attrs.getType() == FileAttributes.FileType.Directory
            && !attrs.getFileName().startsWith(StorageContext.RESERVED_NAMESPACE_DIR_PREFIX);
    }

    /**
     * True when a child name collides with the {@code executions/} path segment. Storage cannot distinguish
     * a flow named {@code executions} from a sub-namespace whose last segment is {@code executions}, so
     * namespace-scoped scans skip the ambiguous name and require explicit {@code flowId} to act on it.
     */
    private boolean isAmbiguousFlowName(String childName, @Nullable String namespace) {
        if (!StorageContext.EXECUTIONS_DIR_NAME.equals(childName)) {
            return false;
        }
        log.warn("Skipping ambiguous flow/sub-namespace path segment '{}' under namespace '{}'; target the flow explicitly via 'flowId' to purge it.",
            childName, namespace);
        return true;
    }

    /**
     * Lists {@code uri} returning empty on missing paths and on any I/O error (logged at WARN). The
     * {@code namespace} is threaded through so backends enforcing per-namespace isolation (e.g. dedicated
     * storage) receive the correct scope; only the very top of a tenant-wide walk passes {@code null}.
     */
    private List<FileAttributes> safeList(@Nullable String tenantId, @Nullable String namespace, URI uri) {
        try {
            return storageInterface.list(tenantId, namespace, uri);
        } catch (FileNotFoundException | NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            log.warn("Failed to list storage at '{}'; skipping this subtree.", uri, e);
            return List.of();
        }
    }

    /**
     * Result of a {@link #purgeByLastModified} run. {@code purgedCount} counts distinct execution IDs that
     * had at least one file in the window (or would in dry-run mode); {@code deletedFilesCount} is the
     * raw file count (always 0 in dry-run).
     */
    public record StoragePurgeResult(int scannedCount, int purgedCount, int deletedFilesCount) { }

    private static final class Aggregator {
        int scanned;
        int deletedFiles;
        final Set<String> purgedExecutions = new LinkedHashSet<>();
    }
}
