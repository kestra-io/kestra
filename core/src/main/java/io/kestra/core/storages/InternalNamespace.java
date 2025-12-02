package io.kestra.core.storages;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link Namespace} implementation.
 * This class acts as a facade to the {@link StorageInterface} for manipulating namespace files.
 *
 * @see Storage#namespace()
 * @see Storage#namespace(String)
 */
public class InternalNamespace implements Namespace {

    private static final Logger LOG = LoggerFactory.getLogger(InternalNamespace.class);

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;
    private final NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;
    private final Logger logger;

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param namespace The namespace
     * @param storage   The storage.
     */
    public InternalNamespace(@Nullable final String tenant, final String namespace, final StorageInterface storage, final NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository) {
        this(LOG, tenant, namespace, storage, namespaceFileMetadataRepository);
    }

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param logger    The logger to be used by this class.
     * @param namespace The namespace
     * @param tenant    The tenant.
     * @param storage   The storage.
     */
    public InternalNamespace(final Logger logger, @Nullable final String tenant, final String namespace, final StorageInterface storage, final NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepositoryInterface) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.namespaceFileMetadataRepository = Objects.requireNonNull(namespaceFileMetadataRepositoryInterface, "namespaceFileMetadataRepository cannot be null");
        this.tenant = tenant;
    }

    @Override
    public ArrayListTotal<NamespaceFile> find(Pageable pageable, List<QueryFilter> filters, boolean allowDeleted, FetchVersion fetchVersion) {
        return namespaceFileMetadataRepository.find(
            pageable,
            tenant,
            Stream.concat(filters.stream(), Stream.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build()
            )).toList(),
            allowDeleted,
            fetchVersion
        ).map(throwFunction(NamespaceFile::fromMetadata));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public String tenantId() {
        return tenant;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> all() throws IOException {
        return all(null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> all(final String containing, boolean includeDirectories) throws IOException {
        List<NamespaceFileMetadata> namespaceFilesMetadata = namespaceFileMetadataRepository.find(Pageable.UNPAGED, tenant, Stream.concat(
            Stream.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build()),
            Optional.ofNullable(containing).flatMap(p -> {
                if (p.equals("/")) {
                    return Optional.empty();
                }

                return Optional.of(QueryFilter.builder().field(QueryFilter.Field.QUERY).operation(QueryFilter.Op.EQUALS).value(p).build());
            }).stream()
        ).toList(), false);

        if (!includeDirectories) {
            namespaceFilesMetadata = namespaceFilesMetadata.stream().filter(nsFileMetadata -> !nsFileMetadata.isDirectory()).toList();
        }

        return namespaceFilesMetadata.stream().filter(nsFileMetadata -> !nsFileMetadata.getPath().equals("/")).map(nsFileMetadata -> NamespaceFile.of(namespace, Path.of(nsFileMetadata.getPath()), nsFileMetadata.getVersion())).toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFileMetadata> children(String parentPath, boolean recursive) throws IOException {
        final String normalizedParentPath = NamespaceFile.normalize(Path.of(parentPath), true).toString();

        return namespaceFileMetadataRepository.find(Pageable.UNPAGED, tenant, List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.PARENT_PATH)
                .operation(recursive ? QueryFilter.Op.STARTS_WITH : QueryFilter.Op.EQUALS)
                .value(normalizedParentPath.endsWith("/") ? normalizedParentPath : normalizedParentPath + "/")
                .build()
        ), false);
    }

    @Override
    public List<Pair<NamespaceFile, NamespaceFile>> move(Path source, Path target) throws Exception {
        final Path normalizedSource = NamespaceFile.normalize(source, true);
        final Path normalizedTarget = NamespaceFile.normalize(target, true);

        if (findByPath(normalizedTarget).isPresent()) {
            throw new IOException(String.format(
                "File '%s' already exists in namespace '%s'.",
                normalizedTarget,
                namespace
            ));
        }

        ArrayListTotal<NamespaceFileMetadata> beforeRename = namespaceFileMetadataRepository.find(Pageable.UNPAGED, tenant, List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
            QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.IN).value(List.of(normalizedSource.toString(), normalizedSource + "/")).build()
        ), true, FetchVersion.ALL);
        beforeRename.sort(Comparator.comparing(NamespaceFileMetadata::getVersion));
        ArrayListTotal<NamespaceFileMetadata> afterRename = beforeRename
            .map(nsFileMetadata -> {
                String newPath;
                if (nsFileMetadata.isDirectory()) {
                    newPath = normalizedTarget.toString().endsWith("/") ? normalizedTarget.toString() : normalizedTarget + "/";
                } else {
                    newPath = normalizedTarget.toString();
                }

                return nsFileMetadata.toBuilder().path(newPath).build();
            });

        return afterRename.map(throwFunction(nsFileMetadata -> {
            NamespaceFile beforeNamespaceFile = NamespaceFile.of(namespace, normalizedSource, nsFileMetadata.getVersion());
            Path namespaceFilePath = beforeNamespaceFile.storagePath();
            NamespaceFile afterNamespaceFile;
            if (nsFileMetadata.isDirectory()) {
                afterNamespaceFile = this.createDirectory(Path.of(nsFileMetadata.getPath()));
            } else {
                try (InputStream oldContent = storage.get(tenant, namespace, namespaceFilePath.toUri())) {
                    afterNamespaceFile = this.putFile(Path.of(nsFileMetadata.getPath()), oldContent, Conflicts.OVERWRITE).getFirst();
                }
            }

            this.purge(NamespaceFile.of(namespace, normalizedSource, nsFileMetadata.getVersion()));
            return Pair.of(beforeNamespaceFile, afterNamespaceFile);
        }));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public NamespaceFile get(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        int version = findByPath(normalizedPath).map(NamespaceFileMetadata::getVersion).orElse(1);

        return NamespaceFile.of(namespace, normalizedPath, version);
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
    public InputStream getFileContent(Path path, @Nullable Integer version) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        // Throw if file not found OR if it's deleted
        NamespaceFileMetadata namespaceFileMetadata = findByPath(normalizedPath, version).orElseThrow(() -> fileNotFound(normalizedPath, version));

        Path namespaceFilePath = NamespaceFile.of(namespace, normalizedPath, namespaceFileMetadata.getVersion()).storagePath();
        return storage.get(tenant, namespace, namespaceFilePath.toUri());
    }

    @Override
    public FileAttributes getFileMetadata(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        return findByPath(normalizedPath).map(NamespaceFileAttributes::new).orElseThrow(() -> fileNotFound(normalizedPath, null));
    }

    private FileNotFoundException fileNotFound(Path path, @Nullable Integer version) {
        return new FileNotFoundException(Optional.ofNullable(version).map(v -> "Version " + v + " of file").orElse("File") + " '" + path + "' was not found in namespace '" + namespace + "'.");
    }

    private Optional<NamespaceFileMetadata> findByPath(Path path, boolean allowDeleted, @Nullable Integer version) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        if (version != null) {
            return namespaceFileMetadataRepository.find(Pageable.from(1, 1), tenant, List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
                QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.EQUALS).value(normalizedPath.toString()).build(),
                QueryFilter.builder().field(QueryFilter.Field.VERSION).operation(QueryFilter.Op.EQUALS).value(version).build()
            ), allowDeleted, FetchVersion.ALL).stream().findFirst();
        }
        return namespaceFileMetadataRepository.findByPath(tenant, namespace, normalizedPath.toString())
            .filter(namespaceFileMetadata -> allowDeleted || !namespaceFileMetadata.isDeleted());
    }

    private Optional<NamespaceFileMetadata> findByPath(Path path, boolean allowDeleted) throws IOException {
        return findByPath(path, allowDeleted, null);
    }

    private Optional<NamespaceFileMetadata> findByPath(Path path, @Nullable Integer version) throws IOException {
        return findByPath(path, false, version);
    }

    private Optional<NamespaceFileMetadata> findByPath(Path path) throws IOException {
        return findByPath(path, null);
    }

    @Override
    public boolean exists(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        return findByPath(normalizedPath).isPresent();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> putFile(final Path path, final InputStream content, final Conflicts onAlreadyExist) throws IOException, URISyntaxException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        Optional<NamespaceFileMetadata> inRepository = findByPath(normalizedPath, true);
        int currentVersion = inRepository.map(NamespaceFileMetadata::getVersion).orElse(0);
        NamespaceFile namespaceFile = NamespaceFile.of(namespace, normalizedPath, currentVersion + 1);
        Path storagePath = namespaceFile.storagePath();
        // Remove Windows letter
        URI cleanUri = new URI(storagePath.toUri().toString().replaceFirst("^file:///[a-zA-Z]:", ""));

        List<NamespaceFile> createdFiles = new ArrayList<>();
        if (inRepository.isEmpty()) {
            storage.put(tenant, namespace, cleanUri, content);

            createdFiles.addAll(mkDirs(normalizedPath.toString()));

            namespaceFileMetadataRepository.save(
                NamespaceFileMetadata.builder()
                    .tenantId(tenant)
                    .namespace(namespace)
                    .path(normalizedPath.toString())
                    .size(storage.getAttributes(tenant, namespace, cleanUri).getSize())
                    .build()
            );

            logger.debug(String.format(
                "File '%s' added to namespace '%s'.",
                normalizedPath,
                namespace
            ));

            createdFiles.add(namespaceFile);
        } else if (onAlreadyExist == Conflicts.OVERWRITE || inRepository.get().isDeleted()) {
            storage.put(tenant, namespace, cleanUri, content);

            createdFiles.addAll(mkDirs(normalizedPath.toString()));

            namespaceFileMetadataRepository.save(
                inRepository.get().toBuilder().size(storage.getAttributes(tenant, namespace, cleanUri).getSize()).deleted(false).build()
            );

            if (inRepository.get().isDeleted()) {
                logger.debug(String.format(
                    "File '%s' added to namespace '%s'.",
                    normalizedPath,
                    namespace
                ));
            } else {
                logger.debug(String.format(
                    "File '%s' overwritten into namespace '%s'.",
                    normalizedPath,
                    namespace
                ));
            }

            createdFiles.add(namespaceFile);
        } else {
            // At this point, the file exists and we have to decide what to do based on the conflict strategy
            switch (onAlreadyExist) {
                case ERROR -> throw new IOException(String.format(
                    "File '%s' already exists in namespace '%s' and conflict is set to %s",
                    normalizedPath,
                    namespace,
                    Conflicts.ERROR
                ));
                case SKIP -> logger.debug(String.format(
                    "File '%s' already exists in namespace '%s' and conflict is set to %s. Skipping.",
                    normalizedPath,
                    namespace,
                    Conflicts.SKIP
                ));
            }
        }

        return createdFiles;
    }

    /**
     * Make all parent directories for a given path.
     */
    private List<NamespaceFile> mkDirs(String path) throws IOException {
        List<NamespaceFile> createdDirs = new ArrayList<>();
        Optional<Path> maybeParentPath = Optional.empty();
        while (
            (maybeParentPath = Optional.ofNullable(NamespaceFileMetadata.parentPath(maybeParentPath.map(Path::toString).orElse(path))).map(Path::of)).isPresent()
                && !this.exists(maybeParentPath.get())
        ) {
            this.createDirectory(maybeParentPath.get());
            createdDirs.add(NamespaceFile.of(namespace, maybeParentPath.get().toString().endsWith("/") ? maybeParentPath.get().toString() : maybeParentPath.get() + "/", 1));
        }

        return createdDirs;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public NamespaceFile createDirectory(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        NamespaceFileMetadata nsFileMetadata = namespaceFileMetadataRepository.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenant)
                .namespace(namespace)
                .path(normalizedPath.toString().endsWith("/") ? normalizedPath.toString() : normalizedPath + "/")
                .size(0L)
                .build()
        );
        storage.createDirectory(tenant, namespace, NamespaceFile.of(namespace, normalizedPath, 1).storagePath().toUri());

        return NamespaceFile.fromMetadata(nsFileMetadata);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> delete(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path, true);

        Optional<NamespaceFileMetadata> maybeNamespaceFileMetadata = namespaceFileMetadataRepository.find(Pageable.from(1, 1), tenant, List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
            QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.IN).value(List.of(normalizedPath.toString(), normalizedPath + "/")).build()
        ), false).stream().findFirst();

        List<NamespaceFileMetadata> toDelete = Stream.concat(
            this.children(normalizedPath.toString(), true).stream().map(NamespaceFileMetadata::toDeleted),
            maybeNamespaceFileMetadata.map(NamespaceFileMetadata::toDeleted).stream()
        ).toList();

        toDelete.forEach(namespaceFileMetadataRepository::save);

        return toDelete.stream().map(NamespaceFile::fromMetadata).toList();
    }

    @Override
    public boolean purge(NamespaceFile namespaceFile) throws IOException {
        storage.delete(tenant, namespace, namespaceFile.storagePath().toUri());
        namespaceFileMetadataRepository.purge(List.of(NamespaceFileMetadata.of(tenant, namespaceFile)));
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer purge(List<NamespaceFile> namespaceFiles) throws IOException {
        Integer purgedMetadataCount = this.namespaceFileMetadataRepository.purge(namespaceFiles.stream().map(namespaceFile -> NamespaceFileMetadata.of(tenant, namespaceFile)).toList());

        long actualDeletedEntries = namespaceFiles.stream()
            .map(NamespaceFile::storagePath)
            .map(Path::toUri)
            .map(throwFunction(uri -> this.storage.delete(tenant, namespace, uri)))
            .filter(Boolean::booleanValue)
            .count();

        if (actualDeletedEntries != purgedMetadataCount) {
            LOG.warn("Namespace Files Metadata purge reported {} deleted entries, but {} values were actually deleted from storage", purgedMetadataCount, actualDeletedEntries);
        }

        return purgedMetadataCount;
    }
}
