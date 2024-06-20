package io.kestra.core.runners;

import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.storages.InternalNamespace;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Rethrow;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated should not be used anymore
 */
@Deprecated
@Singleton
public class NamespaceFilesService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceFilesService.class);

    private final StorageInterface storageInterface;

    @Inject
    public NamespaceFilesService(final StorageInterface storageInterface) {
        this.storageInterface = storageInterface;
    }

    @Deprecated
    public List<URI> inject(RunContext runContext, String tenantId, String namespace, Path basePath, NamespaceFiles namespaceFiles) throws Exception {
        if (!Boolean.TRUE.equals(namespaceFiles.getEnabled())) {
            return Collections.emptyList();
        }

        List<NamespaceFile> matchedNamespaceFiles = namespaceFor(tenantId, namespace)
            .findAllFilesMatching(namespaceFiles.getInclude(), namespaceFiles.getExclude());

        matchedNamespaceFiles.forEach(Rethrow.throwConsumer(namespaceFile -> {
            InputStream content = storageInterface.get(tenantId, namespaceFile.uri());
            runContext.workingDir().putFile(namespaceFile.path(), content);
        }));
        return matchedNamespaceFiles.stream().map(NamespaceFile::path).map(Path::toUri).toList();
    }

    public URI uri(String namespace, @Nullable URI path) {
        return URI.create(StorageContext.namespaceFilePrefix(namespace) + Optional.ofNullable(path)
            .map(URI::getPath)
            .orElse("")
        );
    }

    public List<URI> recursiveList(String tenantId, String namespace, @Nullable URI path) throws IOException {
        return recursiveList(tenantId, namespace, path, false);
    }

    public List<URI> recursiveList(String tenantId, String namespace, @Nullable URI path, boolean includeDirectoryEntries) throws IOException {
        return namespaceFor(tenantId, namespace)
            .all(path.getPath(), includeDirectoryEntries)
            .stream()
            .map(NamespaceFile::path)
            .map(Path::toUri)
            .toList();
    }

    private InternalNamespace namespaceFor(String tenantId, String namespace) {
        return new InternalNamespace(log, tenantId, namespace, storageInterface);
    }

    public boolean delete(String tenantId, String namespace, URI path) throws IOException {
        return namespaceFor(tenantId, namespace).delete(Path.of(path));
    }

    public URI createFile(String tenantId, String namespace, URI path, InputStream inputStream) throws IOException {
        return namespaceFor(tenantId, namespace).putFile(Path.of(path), inputStream, Namespace.Conflicts.OVERWRITE)
            .path()
            .toUri();
    }

    public URI createDirectory(String tenantId, String namespace, URI path) throws IOException {
        return namespaceFor(tenantId, namespace).createDirectory(Path.of(path.getPath()));
    }

    public InputStream content(String tenantId, String namespace, URI path) throws IOException {
        return namespaceFor(tenantId, namespace).getFileContent(Path.of(path));
    }

    public static URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return NamespaceFile.of(namespace, relativePath).uri();
    }
}
