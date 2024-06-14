package io.kestra.core.storages;

import io.kestra.core.services.FlowService;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The default {@link Storage} implementation acting as a facade to the {@link StorageInterface}.
 */
public class InternalStorage implements Storage {

    private static final Logger LOG = LoggerFactory.getLogger(InternalStorage.class);

    private static final String PATH_SEPARATOR = "/";

    private final Logger logger;
    private final StorageContext context;
    private final StorageInterface storage;
    private final FlowService flowService;

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param context The storage context.
     * @param storage The storage to delegate operations.
     */
    public InternalStorage(StorageContext context, StorageInterface storage) {
        this(LOG, context, storage, null);
    }

    /**
     * Creates a new {@link InternalStorage} instance.
     *
     * @param logger  The logger to be used by this class.
     * @param context The storage context.
     * @param storage The storage to delegate operations.
     */
    public InternalStorage(Logger logger, StorageContext context, StorageInterface storage, FlowService flowService) {
        this.logger = logger;
        this.context = context;
        this.storage = storage;
        this.flowService = flowService;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace() {
        return new InternalNamespace(logger, context.getTenantId(), context.getNamespace(), storage);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Namespace namespace(String namespace) {
        boolean isExternalNamespace = !namespace.equals(context.getNamespace());
        // Checks whether the contextual namespace is allowed to access the passed namespace.
        if (isExternalNamespace && flowService != null) {
            flowService.checkAllowedNamespace(
                context.getTenantId(), namespace, // requested Tenant/Namespace
                context.getTenantId(), context.getNamespace() // from Tenant/Namespace
            );
        }
        return new InternalNamespace(logger, context.getTenantId(), namespace, storage);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean isFileExist(URI uri) {
        return this.storage.exists(context.getTenantId(), uri);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getFile(final URI uri) throws IOException {
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

        return this.storage.get(context.getTenantId(), uri);

    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<URI> deleteExecutionFiles() throws IOException {
        return this.storage.deleteByPrefix(context.getTenantId(), context.getExecutionStorageURI());
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
        URI resolved = uri.resolve(uri.getPath() + PATH_SEPARATOR + name);
        return this.storage.put(context.getTenantId(), resolved, new BufferedInputStream(inputStream));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putFile(InputStream inputStream, URI uri) throws IOException {
        return this.storage.put(context.getTenantId(), uri, new BufferedInputStream(inputStream));
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
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete temporary file '{}'", file.toPath(), e);
            }
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getTaskStateFile(String state, String name) throws IOException {
        return getTaskStateFile(state, name, false, true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        URI uri = URI.create(getStatePrefix(state, isNamespace, useTaskRun));
        URI resolve = uri.resolve(uri.getPath() + PATH_SEPARATOR + name);
        return this.storage.get(context.getTenantId(), resolve);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putTaskStateFile(byte[] content, String state, String name) throws IOException {
        return this.putTaskStateFile(content, state, name, false, true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putTaskStateFile(byte[] content, String state, String name, Boolean namespace, Boolean useTaskRun) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            return this.putFile(inputStream, getStatePrefix(state, namespace, useTaskRun), name);
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putTaskStateFile(File file, String state, String name) throws IOException {
        return this.putTaskStateFile(file, state, name, false, true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI putTaskStateFile(File file, String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        return this.putFileAndDelete(file, getStatePrefix(state, isNamespace, useTaskRun), name);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean deleteTaskStateFile(String state, String name) throws IOException {
        return this.deleteTaskStateFile(state, name, false, true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean deleteTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException {
        URI uri = URI.create(getStatePrefix(state, isNamespace, useTaskRun));
        URI resolve = uri.resolve(uri.getPath() + PATH_SEPARATOR + name);
        return this.storage.delete(context.getTenantId(), resolve);
    }

    private String getStatePrefix(String name, Boolean isNamespace, Boolean useTaskRun) {
        String value = useTaskRun ? getTaskStorageContext().map(StorageContext.Task::getTaskRunValue).orElse(null) : null;
        return context.getStateStorePrefix(name, isNamespace, value);

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
                    logger.debug("Cache is expired for cache-id={}, object-id={}, and ttl={}, deleting it",
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
        return isFileExist(uri) ?
            Optional.of(storage.get(context.getTenantId(), uri)) :
            Optional.empty();
    }

    private Optional<Long> getCacheFileLastModifiedTime(String cacheId, @Nullable String objectId) throws IOException {
        URI uri = context.getCacheURI(cacheId, objectId);
        return isFileExist(uri) ?
            Optional.of(this.storage.getAttributes(context.getTenantId(), uri).getLastModifiedTime()) :
            Optional.empty();
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
        return isFileExist(uri) ?
            Optional.of(this.storage.delete(context.getTenantId(), uri)) :
            Optional.empty();
    }

    private URI putFileAndDelete(File file, URI uri) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return this.putFile(is, uri);
        } finally {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete temporary file '{}'", file.toPath(), e);
            }
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
        return this.storage.put(context.getTenantId(), resolve, new BufferedInputStream(inputStream));
    }

    private Optional<StorageContext.Task> getTaskStorageContext() {
        return Optional.ofNullable((context instanceof StorageContext.Task task) ? task : null);
    }
}
