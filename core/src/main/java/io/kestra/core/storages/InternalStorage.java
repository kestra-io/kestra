package io.kestra.core.storages;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import io.kestra.core.services.NamespaceService;
import io.kestra.core.utils.FileUtils;
import io.kestra.core.utils.Slugify;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.storages.NamespaceFile.toLogicalPath;

/**
 * The default {@link Storage} implementation acting as a facade to the {@link StorageInterface}.
 */
@Slf4j
public class InternalStorage implements Storage {

    private static final String PATH_SEPARATOR = "/";
    // {cache-id}/cache/..., relative to the flow storage folder
    private static final Pattern OWN_FLOW_CACHE = Pattern.compile("[^/]+/cache/.*");

    private final Logger logger;
    private final StorageContext context;
    private final StorageInterface storage;
    private final NamespaceFactory namespaceFactory;
    private final NamespaceService namespaceService;
    @Nullable
    private final String triggeringExecutionPrefix;

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param logger The logger to be used by this class.
     * @param context The storage context.
     * @param storage The storage to delegate operations.
     */
    public InternalStorage(Logger logger, StorageContext context, StorageInterface storage, NamespaceService namespaceService, NamespaceFactory namespaceFactory) {
        this(logger, context, storage, namespaceService, namespaceFactory, null);
    }

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param triggeringExecutionPrefix The storage path prefix of the execution that triggered the current one, see
     *                                  {@link StorageContext#triggeringExecutionPrefix(java.util.Map)}.
     */
    public InternalStorage(Logger logger, StorageContext context, StorageInterface storage, NamespaceService namespaceService, NamespaceFactory namespaceFactory, @Nullable String triggeringExecutionPrefix) {
        this.triggeringExecutionPrefix = triggeringExecutionPrefix;
        this.logger = logger;
        this.context = context;
        this.storage = storage;
        this.namespaceService = Objects.requireNonNull(namespaceService);
        this.namespaceFactory = namespaceFactory;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace() {
        return namespaceFactory.of(logger, context.getTenantId(), context.getNamespace());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace(String namespace) {
        boolean isExternalNamespace = !namespace.equals(context.getNamespace());
        // Checks whether the contextual namespace is allowed to access the passed namespace.
        if (isExternalNamespace) {
            namespaceService.checkAllowedNamespace(
                context.getTenantId(), namespace, // requested Tenant/Namespace
                context.getTenantId(), context.getNamespace() // from Tenant/Namespace
            );
        }
        return namespaceFactory.of(logger, context.getTenantId(), namespace);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean isFileExist(URI uri) {
        checkAccess(uri);

        return exists(uri);
    }

    private boolean exists(URI uri) {
        return this.storage.exists(context.getTenantId(), context.getNamespace(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getFile(final URI uri) throws IOException {
        checkAccess(uri);

        return this.storage.get(context.getTenantId(), context.getNamespace(), uri);

    }

    @Override
    public FileAttributes getAttributes(URI uri) throws IOException {
        checkAccess(uri);

        return this.storage.getAttributes(context.getTenantId(), context.getNamespace(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean deleteFile(final URI uri) throws IOException {
        checkAccess(uri);

        return this.storage.delete(context.getTenantId(), context.getNamespace(), uri);

    }

    /**
     * Rejects a URI that points outside what the current flow may reach: its own flow and namespace storage, the
     * execution that triggered it, or a namespace allowed by the {@link NamespaceService}.
     */
    private void checkAccess(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("The internal storage URI must not be null.");
        }
        if (uri.getScheme() != null && !StorageContext.KESTRA_SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException("The internal storage URI '%s' must use the '%s' scheme.".formatted(uri, StorageContext.KESTRA_SCHEME));
        }
        String path = uri.getPath();
        if (path == null || FileUtils.isParentTraversal(uri)) {
            throw new IllegalArgumentException("The internal storage URI '%s' must have a path without parent directory traversal.".formatted(uri));
        }

        String directoryPath = path.endsWith(PATH_SEPARATOR) ? path : path + PATH_SEPARATOR;
        if (isInOwnStorage(uri, directoryPath) || (triggeringExecutionPrefix != null && directoryPath.startsWith(triggeringExecutionPrefix))) {
            return;
        }

        // a candidate that is not a real namespace has no restriction of its own but would inherit its parents' ones
        StorageContext.candidateNamespaces(uri).stream()
            .filter(namespace -> !isSelfOrAncestor(namespace))
            .filter(namespace -> !namespaceService.isAllowedNamespace(context.getTenantId(), namespace, context.getTenantId(), context.getNamespace()))
            .filter(namespace -> namespaceService.isNamespaceExists(context.getTenantId(), namespace))
            .forEach(namespace -> namespaceService.checkAllowedNamespace(context.getTenantId(), namespace, context.getTenantId(), context.getNamespace()));
    }

    private boolean isSelfOrAncestor(String namespace) {
        String own = context.getNamespace();
        return own != null && (own.equals(namespace) || own.startsWith(namespace + "."));
    }

    private boolean isInOwnStorage(URI uri, String directoryPath) {
        if (context.getNamespace() == null || context.getFlowId() == null) {
            return false;
        }

        String namespacePath = PATH_SEPARATOR + context.getNamespaceAsPath() + PATH_SEPARATOR;
        String flowPath = namespacePath + Slugify.of(context.getFlowId()) + PATH_SEPARATOR;
        return directoryPath.startsWith(flowPath + StorageContext.EXECUTIONS_DIR_NAME + PATH_SEPARATOR)
            || directoryPath.startsWith(flowPath + "states" + PATH_SEPARATOR)
            || (directoryPath.startsWith(flowPath) && OWN_FLOW_CACHE.matcher(directoryPath.substring(flowPath.length())).matches())
            || directoryPath.startsWith(namespacePath + "states" + PATH_SEPARATOR)
            || StorageContext.isInNamespaceFiles(context.getNamespace(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<URI> deleteExecutionFiles() throws IOException {
        return this.storage.deleteByPrefix(context.getTenantId(), context.getNamespace(), context.getExecutionStorageURI());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI getContextBaseURI() {
        return this.context.getContextStorageURI();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(InputStream inputStream, String name) throws IOException {
        URI uri = context.getContextStorageURI();
        URI resolved = buildStorageUri(uri, toLogicalPath(name));
        return this.storage.put(context.getTenantId(), context.getNamespace(), resolved, new BufferedInputStream(inputStream));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(InputStream inputStream, URI uri) throws IOException {
        checkAccess(uri);

        return put(inputStream, uri);
    }

    private URI put(InputStream inputStream, URI uri) throws IOException {
        return this.storage.put(context.getTenantId(), context.getNamespace(), uri, new BufferedInputStream(inputStream));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(File file) throws IOException {
        return putFile(file, null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(File file, String name) throws IOException {
        URI uri = context.getContextStorageURI();
        URI resolved = buildStorageUri(uri, name != null ? name : file.getName());
        try (InputStream is = new FileInputStream(file)) {
            return put(is, resolved);
        } finally {
            FileUtils.deleteWithRetry(file.toPath())
                .ifPresent(e -> logger.warn("Failed to delete temporary file '{}'", file.toPath(), e));
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<InputStream> getCacheFile(final String cacheId,
        final @Nullable String objectId,
        final @Nullable Duration ttl) throws IOException {
        if (ttl != null) {
            var maybeLastModifiedTime = getCacheFileLastModifiedTime(cacheId, objectId);
            if (maybeLastModifiedTime.isPresent()) {
                if (Instant.now().isAfter(Instant.ofEpochMilli(maybeLastModifiedTime.get()).plus(ttl))) {
                    logger.debug(
                        "Cache is expired for cache-id={}, object-id={}, and ttl={}, deleting it",
                        cacheId,
                        objectId,
                        ttl.toMillis()
                    );
                    deleteCacheFile(cacheId, objectId);
                    return Optional.empty();
                }
            }
        }
        URI uri = context.getCacheURI(cacheId, objectId);
        return exists(uri) ? Optional.of(storage.get(context.getTenantId(), context.getNamespace(), uri)) : Optional.empty();
    }

    private Optional<Long> getCacheFileLastModifiedTime(String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return exists(uri) ? Optional.of(this.storage.getAttributes(context.getTenantId(), context.getNamespace(), uri).getLastModifiedTime()) : Optional.empty();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putCacheFile(File file, String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return this.putFileAndDelete(file, uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<Boolean> deleteCacheFile(String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return exists(uri) ? Optional.of(this.storage.delete(context.getTenantId(), context.getNamespace(), uri)) : Optional.empty();
    }

    private URI putFileAndDelete(File file, URI uri) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return this.put(is, uri);
        } finally {
            FileUtils.deleteWithRetry(file.toPath())
                .ifPresent(e -> logger.warn("Failed to delete temporary file '{}'", file.toPath(), e));
        }
    }

    private URI putFileAndDelete(File file, String prefix, String name) throws IOException {
        URI uri = URI.create(prefix);
        URI resolve = buildStorageUri(uri, name != null ? name : file.getName());
        return putFileAndDelete(file, resolve);
    }

    private URI putFile(InputStream inputStream, String prefix, String name) throws IOException {
        URI uri = URI.create(prefix);
        URI resolve = buildStorageUri(uri, name);
        return this.storage.put(context.getTenantId(), context.getNamespace(), resolve, new BufferedInputStream(inputStream));
    }

    /**
     * Appends a raw filename to the logical storage path. The name is not concatenated onto
     * {@code base.toString()}, which would treat {@code #} as a fragment and reject a space.
     */
    private static URI buildStorageUri(URI base, String rawName) throws IOException {
        try {
            String basePath = StorageContext.logicalPath(base);
            if (basePath.isEmpty()) {
                basePath = "/";
            }
            if (!basePath.endsWith(PATH_SEPARATOR)) {
                basePath = basePath + PATH_SEPARATOR;
            }
            // Re-render from the logical path. getHost() is null for a legal namespace segment
            // such as my_namespace, and getPath() drops that segment on a canonical URI.
            return StorageContext.toKestraUri(basePath + rawName);
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot build storage URI for file name '%s'.".formatted(rawName), e);
        }
    }

    @Override
    public Optional<StorageContext.Task> getTaskStorageContext() {
        return Optional.ofNullable((context instanceof StorageContext.Task task) ? task : null);
    }

    @Override
    public List<FileAttributes> list(URI uri) throws IOException {
        checkAccess(uri);

        return this.storage.list(context.getTenantId(), context.getNamespace(), uri);
    }
}
