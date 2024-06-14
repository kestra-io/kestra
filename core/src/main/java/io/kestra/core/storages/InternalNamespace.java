package io.kestra.core.storages;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The default {@link Namespace} implementation.
 * This class acts as a facade to the {@link StorageInterface} for manipulating namespace files.
 *
 * @see Storage#namespace()
 * @see Storage#namespace(String)
 */
public class InternalNamespace implements Namespace {

    private static final Logger log = LoggerFactory.getLogger(InternalNamespace.class);

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;
    private final Logger logger;

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param namespace The namespace
     * @param storage   The storage.
     */
    public InternalNamespace(final String namespace, final StorageInterface storage) {
        this(log, null, namespace, storage);
    }

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param logger    The logger to be used by this class.
     * @param namespace The namespace
     * @param tenant    The tenant.
     * @param storage   The storage.
     */
    public InternalNamespace(final Logger logger, @Nullable final String tenant, final String namespace, final StorageInterface storage) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.tenant = tenant;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public String namespace() {
        return namespace;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> all() throws IOException {
        return all(false);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> all(final boolean includeDirectories) throws IOException {
        return all(null, includeDirectories);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> all(final String prefix, final boolean includeDirectories) throws IOException {
        URI namespacePrefix = URI.create(NamespaceFile.of(namespace, Optional.ofNullable(prefix).map(Path::of).orElse(null)).storagePath() + "/");
        return storage.allByPrefix(tenant, namespacePrefix, includeDirectories)
            .stream()
            .map(uri -> new NamespaceFile(relativize(uri), uri, namespace))
            .toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public NamespaceFile get(final Path path) {
        return NamespaceFile.of(namespace, path);
    }

    public Path relativize(final URI uri) {
        return NamespaceFile.of(namespace)
            .storagePath()
            .relativize(Path.of(uri.getPath()));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> findAllFilesMatching(final Predicate<Path> predicate) throws IOException {
        return all().stream().filter(it -> predicate.test(it.path(true))).toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getFileContent(final Path path) throws IOException {
        Path namespaceFilePath = NamespaceFile.of(namespace, path).storagePath();
        return storage.get(tenant, namespaceFilePath.toUri());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public NamespaceFile putFile(final Path path, final InputStream content, final Conflicts onAlreadyExist) throws IOException {
        Path namespaceFilesPrefix = NamespaceFile.of(namespace, path).storagePath();
        final boolean exists = storage.exists(tenant, namespaceFilesPrefix.toUri());

        return switch (onAlreadyExist) {
            case OVERWRITE -> {
                URI uri = storage.put(tenant, namespaceFilesPrefix.toUri(), content);
                NamespaceFile namespaceFile = new NamespaceFile(relativize(uri), uri, namespace);
                if (exists) {
                    logger.debug(String.format(
                        "File '%s' overwritten into namespace '%s'.",
                        path,
                        namespace
                    ));
                } else {
                    logger.debug(String.format(
                        "File '%s' added to namespace '%s'.",
                        path,
                        namespace
                    ));
                }
                yield namespaceFile;
            }
            case ERROR -> {
                if (!exists) {
                    URI uri = storage.put(tenant, namespaceFilesPrefix.toUri(), content);
                    yield new NamespaceFile(relativize(uri), uri, namespace);
                } else {
                    throw new IOException(String.format(
                        "File '%s' already exists in namespace '%s' and conflict is set to %s",
                        path,
                        namespace,
                        Conflicts.ERROR
                    ));
                }
            }
            case SKIP -> {
                if (!exists) {
                    URI uri = storage.put(tenant, namespaceFilesPrefix.toUri(), content);
                    NamespaceFile namespaceFile = new NamespaceFile(relativize(uri), uri, namespace);
                    logger.debug(String.format(
                        "File '%s' added to namespace '%s'.",
                        path,
                        namespace
                    ));
                    yield namespaceFile;
                } else {
                    logger.debug(String.format(
                        "File '%s' already exists in namespace '%s' and conflict is set to %s. Skipping.",
                        path,
                        namespace,
                        Conflicts.SKIP
                    ));
                    URI uri = URI.create(StorageContext.KESTRA_PROTOCOL + namespaceFilesPrefix);
                    yield new NamespaceFile(relativize(uri), uri, namespace);
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public URI createDirectory(Path path) throws IOException {
        return storage.createDirectory(tenant, NamespaceFile.of(namespace, path).storagePath().toUri());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean delete(Path path) throws IOException {
        return storage.delete(tenant, NamespaceFile.of(namespace, path).storagePath().toUri());
    }
}
