package io.kestra.core.storages;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import io.kestra.core.services.NamespaceService;
import io.kestra.core.utils.FileUtils;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.storages.NamespaceFile.toLogicalPath;

/**
 * The default {@link Storage} implementation acting as a facade to the {@link StorageInterface}.
 */
@Slf4j
public class InternalStorage implements Storage {

    private static final String PATH_SEPARATOR = "/";
    private static final String CACHE_EXECUTION_ID_METADATA_KEY = "executionId";

    private final Logger logger;
    private final StorageContext context;
    private final StorageInterface storage;
    private final NamespaceFactory namespaceFactory;
    private final NamespaceService namespaceService;

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param context The storage context.
     * @param storage The storage to delegate operations.
     */
    public InternalStorage(StorageContext context, StorageInterface storage, NamespaceFactory namespaceFactory) {
        this(log, context, storage, null, namespaceFactory);
    }

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param logger The logger to be used by this class.
     * @param context The storage context.
     * @param storage The storage to delegate operations.
     */
    public InternalStorage(Logger logger, StorageContext context, StorageInterface storage, NamespaceService namespaceService, NamespaceFactory namespaceFactory) {
        this.logger = logger;
        this.context = context;
        this.storage = storage;
        this.namespaceService = namespaceService;
        this.namespaceFactory = namespaceFactory;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace() {
        return namespaceFactory.of(logger, context.getTenantId(), context.getNamespace(), storage);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace(String namespace) {
        boolean isExternalNamespace = !namespace.equals(context.getNamespace());
        // Checks whether the contextual namespace is allowed to access the passed namespace.
        if (isExternalNamespace && namespaceService != null) {
            namespaceService.checkAllowedNamespace(
                context.getTenantId(), namespace, // requested Tenant/Namespace
                context.getTenantId(), context.getNamespace() // from Tenant/Namespace
            );
        }
        return namespaceFactory.of(logger, context.getTenantId(), namespace, storage);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean isFileExist(URI uri) {
        return this.storage.exists(context.getTenantId(), context.getNamespace(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getFile(final URI uri) throws IOException {
        uriGuard(uri);

        return this.storage.get(context.getTenantId(), context.getNamespace(), uri);

    }

    @Override
    public FileAttributes getAttributes(URI uri) throws IOException {
        uriGuard(uri);

        return this.storage.getAttributes(context.getTenantId(), context.getNamespace(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean deleteFile(final URI uri) throws IOException {
        uriGuard(uri);

        return this.storage.delete(context.getTenantId(), context.getNamespace(), uri);

    }

    private static void uriGuard(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Invalid internal storage uri, got null");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Invalid internal storage uri, got uri '" + uri + "'");
        }

        if (!scheme.equals("kestra")) {
            throw new IllegalArgumentException("Invalid internal storage scheme, got uri '" + uri + "'");
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<URI> deleteExecutionFiles() throws IOException {
        List<URI> deletedUris = new ArrayList<>(this.storage.deleteByPrefix(context.getTenantId(), context.getNamespace(), context.getExecutionStorageURI()));
        deletedUris.addAll(this.deleteExecutionCacheFiles());
        return deletedUris;
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
        URI resolved = uri.resolve(uri.getPath() + PATH_SEPARATOR + toLogicalPath(name));
        return this.storage.put(context.getTenantId(), context.getNamespace(), resolved, new BufferedInputStream(inputStream));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(InputStream inputStream, URI uri) throws IOException {
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
        URI resolved = uri.resolve(uri.getPath() + PATH_SEPARATOR + (name != null ? name : file.getName()));
        try (InputStream is = new FileInputStream(file)) {
            return putFile(is, resolved);
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
        return isFileExist(uri) ? Optional.of(storage.get(context.getTenantId(), context.getNamespace(), uri)) : Optional.empty();
    }

    private Optional<Long> getCacheFileLastModifiedTime(String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return isFileExist(uri) ? Optional.of(this.storage.getAttributes(context.getTenantId(), context.getNamespace(), uri).getLastModifiedTime()) : Optional.empty();
    }

    private List<URI> deleteExecutionCacheFiles() throws IOException {
        if (context.getExecutionId() == null) {
            return List.of();
        }

        List<URI> deletedCacheFiles = new ArrayList<>();
        List<URI> flowFiles = this.storage.allByPrefix(context.getTenantId(), context.getNamespace(), context.getFlowStorageURI(), false);

        for (URI candidate : flowFiles) {
            if (!isCacheArchive(candidate)) {
                continue;
            }

            try {
                if (cacheBelongsToExecution(candidate, context.getExecutionId()) && this.storage.delete(context.getTenantId(), context.getNamespace(), candidate)) {
                    deletedCacheFiles.add(candidate);
                }
            } catch (IOException | RuntimeException e) {
                logger.warn("Failed to inspect cache file '{}' while purging execution '{}'", candidate, context.getExecutionId(), e);
            }
        }

        return deletedCacheFiles;
    }

    private boolean isCacheArchive(URI candidate) {
        return candidate.getPath() != null && candidate.getPath().contains("/cache/") && candidate.getPath().endsWith("/cache.zip");
    }

    private boolean cacheBelongsToExecution(URI cacheUri, String executionId) throws IOException {
        Map<String, String> metadata = this.storage.getAttributes(context.getTenantId(), context.getNamespace(), cacheUri).getMetadata();
        return metadata != null && executionId.equals(metadata.get(CACHE_EXECUTION_ID_METADATA_KEY));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putCacheFile(File file, String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return this.putFileAndDelete(file, uri, cacheMetadata());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<Boolean> deleteCacheFile(String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return isFileExist(uri) ? Optional.of(this.storage.delete(context.getTenantId(), context.getNamespace(), uri)) : Optional.empty();
    }

    private URI putFileAndDelete(File file, URI uri) throws IOException {
        return putFileAndDelete(file, uri, null);
    }

    private URI putFileAndDelete(File file, URI uri, @Nullable Map<String, String> metadata) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return this.putFile(is, uri, metadata);
        } finally {
            FileUtils.deleteWithRetry(file.toPath())
                .ifPresent(e -> logger.warn("Failed to delete temporary file '{}'", file.toPath(), e));
        }
    }

    private URI putFileAndDelete(File file, String prefix, String name) throws IOException {
        URI uri = URI.create(prefix);
        URI resolve = uri.resolve(uri.getPath() + PATH_SEPARATOR + (name != null ? name : file.getName()));
        return putFileAndDelete(file, resolve);
    }

    private URI putFile(InputStream inputStream, String prefix, String name) throws IOException {
        URI uri = URI.create(prefix);
        URI resolve = uri.resolve(uri.getPath() + PATH_SEPARATOR + name);
        return this.storage.put(context.getTenantId(), context.getNamespace(), resolve, new BufferedInputStream(inputStream));
    }

    private URI putFile(InputStream inputStream, URI uri, @Nullable Map<String, String> metadata) throws IOException {
        return this.storage.put(
            context.getTenantId(),
            context.getNamespace(),
            uri,
            new StorageObject(metadata, new BufferedInputStream(inputStream))
        );
    }

    private @Nullable Map<String, String> cacheMetadata() {
        if (context.getExecutionId() == null) {
            return null;
        }

        return Map.of(CACHE_EXECUTION_ID_METADATA_KEY, context.getExecutionId());
    }

    @Override
    public Optional<StorageContext.Task> getTaskStorageContext() {
        return Optional.ofNullable((context instanceof StorageContext.Task task) ? task : null);
    }

    @Override
    public List<FileAttributes> list(URI uri) throws IOException {
        return this.storage.list(context.getTenantId(), context.getNamespace(), uri);
    }
}
