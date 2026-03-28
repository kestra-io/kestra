package io.kestra.core.storages;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwConsumer;

/**
 * The default {@link Namespace} implementation.
 * This class acts as a facade to the {@link StorageInterface} for manipulating namespace files.
 * <p>
 * This implementation uses {@link NamespaceFileMetadataStateStore} and is safe to call from workers.
 *
 * @see Storage#namespace()
 * @see Storage#namespace(String)
 */
@Slf4j
public class InternalNamespace implements Namespace {

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;
    private final NamespaceFileMetadataStateStore stateStore;
    private final Logger logger;

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param tenant The tenant.
     * @param namespace The namespace.
     * @param storage The storage.
     * @param stateStore The namespace file metadata state store (used for worker-safe operations).
     */
    public InternalNamespace(final String tenant,
        final String namespace,
        final StorageInterface storage,
        final NamespaceFileMetadataStateStore stateStore) {
        this(log, tenant, namespace, storage, stateStore);
    }

    /**
     * Creates a new {@link InternalNamespace} instance.
     *
     * @param logger The logger to be used by this class.
     * @param tenant The tenant.
     * @param namespace The namespace.
     * @param storage The storage.
     * @param stateStore The namespace file metadata state store (used for worker-safe operations).
     */
    public InternalNamespace(final Logger logger,
        final String tenant,
        final String namespace,
        final StorageInterface storage,
        final NamespaceFileMetadataStateStore stateStore) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore cannot be null");
        this.tenant = tenant;
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
        List<NamespaceFileMetadata> namespaceFilesMetadata = stateStore.findAll(tenant, namespace, containing);

        if (!includeDirectories) {
            namespaceFilesMetadata = namespaceFilesMetadata.stream().filter(nsFileMetadata -> !nsFileMetadata.isDirectory()).toList();
        }

        return namespaceFilesMetadata.stream()
            .filter(nsFileMetadata -> !nsFileMetadata.getPath().equals("/"))
            .map(nsFileMetadata -> NamespaceFile.of(namespace, Path.of(nsFileMetadata.getPath()), nsFileMetadata.getVersion()))
            .toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFileMetadata> children(String parentPath, boolean recursive) throws IOException {
        final String normalizedParentPath = NamespaceFile.normalize(Path.of(parentPath)).toString();

        return stateStore.findChildren(tenant, namespace, normalizedParentPath, recursive);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<Pair<NamespaceFile, NamespaceFile>> move(Path source, Path target) throws Exception {
        final Path normalizedSource = NamespaceFile.normalize(source);
        final Path normalizedTarget = NamespaceFile.normalize(target);

        if (exists(normalizedTarget)) {
            throw new IOException(
                String.format(
                    "File '%s' already exists in namespace '%s'.",
                    normalizedTarget,
                    namespace
                )
            );
        }

        // Get all metadata for source and its descendants, all versions
        List<NamespaceFileMetadata> sourceMetas = stateStore.findAllVersionsByPaths(tenant, namespace, List.of(normalizedSource.toString(), normalizedSource + "/"));

        List<NamespaceFileMetadata> allMetas = new ArrayList<>(sourceMetas);
        boolean isDirectory = sourceMetas.stream().anyMatch(NamespaceFileMetadata::isDirectory);
        if (isDirectory) {
            String parentPathPrefix = normalizedSource.toString().endsWith("/") ? normalizedSource.toString() : normalizedSource + "/";
            List<NamespaceFileMetadata> descendants = stateStore.findChildren(tenant, namespace, parentPathPrefix, true);
            allMetas.addAll(descendants);
        }

        allMetas.sort(Comparator.comparing(NamespaceFileMetadata::getVersion));

        // Phase 1: Copy all entries to their new locations, tracking what was created for rollback
        List<Pair<NamespaceFile, NamespaceFile>> results = new ArrayList<>();
        try {
            for (NamespaceFileMetadata nsFileMetadata : allMetas) {
                String oldPath = nsFileMetadata.getPath();
                String relativePart = "";
                if (oldPath.startsWith(normalizedSource.toString())) {
                    relativePart = oldPath.substring(normalizedSource.toString().length());
                }
                String intermediateNewPath = normalizedTarget.toString() + relativePart;
                if (nsFileMetadata.isDirectory() && !intermediateNewPath.endsWith("/")) {
                    intermediateNewPath += "/";
                }
                final String finalNewPath = intermediateNewPath;

                NamespaceFile beforeNamespaceFile = NamespaceFile.of(namespace, Path.of(oldPath), nsFileMetadata.getVersion());
                NamespaceFile afterNamespaceFile;

                if (nsFileMetadata.isDirectory()) {
                    afterNamespaceFile = this.createDirectory(Path.of(finalNewPath));
                } else {
                    try (InputStream oldContent = storage.get(tenant, namespace, beforeNamespaceFile.storagePath().toUri())) {
                        List<NamespaceFile> putResult = this.putFile(Path.of(finalNewPath), oldContent, Conflicts.OVERWRITE);
                        afterNamespaceFile = putResult.stream().filter(f -> f.path().equals(finalNewPath)).findFirst().orElse(putResult.get(putResult.size() - 1));
                    }
                }

                results.add(Pair.of(beforeNamespaceFile, afterNamespaceFile));
            }
        } catch (Exception e) {
            // Rollback: purge all already-created target entries (longest paths first to handle children before parents)
            logger.warn(
                "Move from '{}' to '{}' failed after creating {} of {} entries, rolling back.",
                normalizedSource, normalizedTarget, results.size(), allMetas.size(), e
            );
            results.stream()
                .sorted(Comparator.comparing((Pair<NamespaceFile, NamespaceFile> p) -> p.getRight().path().length()).reversed())
                .forEach(pair ->
                {
                    try {
                        this.purge(pair.getRight());
                    } catch (IOException rollbackEx) {
                        logger.error("Failed to rollback created file '{}' during move rollback.", pair.getRight().path(), rollbackEx);
                    }
                });
            throw new IOException(
                String.format(
                    "Failed to move '%s' to '%s' in namespace '%s'. All changes have been rolled back.",
                    normalizedSource, normalizedTarget, namespace
                ), e
            );
        }

        // Phase 2: All copies succeeded — now purge the source entries
        results.stream()
            .sorted(Comparator.comparing((Pair<NamespaceFile, NamespaceFile> p) -> p.getLeft().path().length()).reversed())
            .forEach(throwConsumer(pair -> this.purge(pair.getLeft())));

        return results;
    }

    private void purge(NamespaceFile nsFile) throws IOException {
        // Hard-delete the old entry via storage
        storage.delete(tenant, namespace, nsFile.storagePath().toUri());
        // Purge the old metadata entry
        stateStore.save(NamespaceFileMetadata.of(tenant, nsFile).toBuilder().deleted(true).build());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public NamespaceFile get(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path);

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
        return all().stream().filter(it -> predicate.test(it.filePath())).toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public InputStream getFileContent(Path path, @Nullable Integer version) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path);

        // Throw if file not found OR if it's deleted
        NamespaceFileMetadata namespaceFileMetadata = findByPath(normalizedPath, version).orElseThrow(() -> fileNotFound(normalizedPath, version));

        Path namespaceFilePath = NamespaceFile.of(namespace, normalizedPath, namespaceFileMetadata.getVersion()).storagePath();
        return storage.get(tenant, namespace, namespaceFilePath.toUri());
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public FileAttributes getFileMetadata(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path);

        return findByPath(normalizedPath).map(NamespaceFileAttributes::new).orElseThrow(() -> fileNotFound(normalizedPath, null));
    }

    private FileNotFoundException fileNotFound(Path path, @Nullable Integer version) {
        return new FileNotFoundException(Optional.ofNullable(version).map(v -> "Version " + v + " of file").orElse("File") + " '" + path + "' was not found in namespace '" + namespace + "'.");
    }

    private Optional<NamespaceFileMetadata> findByPath(Path path, boolean allowDeleted, @Nullable Integer version) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path);

        return stateStore.findByPath(tenant, namespace, normalizedPath.toString(), version, allowDeleted);
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

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean exists(Path path) throws IOException {
        final Path normalizedPath = NamespaceFile.normalize(path);
        return findByPath(normalizedPath).isPresent();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<NamespaceFile> putFile(final Path path, final InputStream content, final Conflicts onAlreadyExist) throws IOException, URISyntaxException {
        final Path normalizedPath = NamespaceFile.normalize(path);

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

            stateStore.save(
                NamespaceFileMetadata.builder()
                    .tenantId(tenant)
                    .namespace(namespace)
                    .path(normalizedPath.toString())
                    .size(storage.getAttributes(tenant, namespace, cleanUri).getSize())
                    .build()
            );

            logger.debug(
                String.format(
                    "File '%s' added to namespace '%s'.",
                    normalizedPath,
                    namespace
                )
            );

            createdFiles.add(namespaceFile);
        } else if (onAlreadyExist == Conflicts.OVERWRITE || inRepository.get().isDeleted()) {
            storage.put(tenant, namespace, cleanUri, content);

            createdFiles.addAll(mkDirs(normalizedPath.toString()));

            stateStore.save(
                inRepository.get().toBuilder().size(storage.getAttributes(tenant, namespace, cleanUri).getSize()).deleted(false).build()
            );

            if (inRepository.get().isDeleted()) {
                logger.debug("File '{}' added to namespace '{}'.", normalizedPath, namespace);
            } else {
                logger.debug("File '{}' overwritten into namespace '{}'.", normalizedPath, namespace);
            }

            createdFiles.add(namespaceFile);
        } else {
            // At this point, the file exists and we have to decide what to do based on the conflict strategy
            switch (onAlreadyExist) {
                case ERROR -> throw new IOException(
                    String.format(
                        "File '%s' already exists in namespace '%s' and conflict is set to %s",
                        normalizedPath,
                        namespace,
                        Conflicts.ERROR
                    )
                );
                case SKIP -> logger.debug("File '{}' already exists in namespace '{}' and conflict is set to {}. Skipping.", normalizedPath, namespace, Conflicts.SKIP);
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
        final Path normalizedPath = NamespaceFile.normalize(path);

        NamespaceFileMetadata nsFileMetadata = stateStore.save(
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
        final Path normalizedPath = NamespaceFile.normalize(path);

        List<NamespaceFileMetadata> matchingFiles = stateStore.findByPaths(
            tenant, namespace,
            List.of(normalizedPath.toString(), normalizedPath + "/"),
            false
        );
        Optional<NamespaceFileMetadata> maybeNamespaceFileMetadata = matchingFiles.stream().findFirst();

        List<NamespaceFileMetadata> toDelete = new ArrayList<>();
        toDelete.addAll(this.children(normalizedPath.toString(), true).stream().map(NamespaceFileMetadata::toDeleted).toList());
        maybeNamespaceFileMetadata.map(NamespaceFileMetadata::toDeleted).ifPresent(toDelete::add);

        toDelete.forEach(stateStore::save);

        return toDelete.stream().map(NamespaceFile::fromMetadata).toList();
    }
}
