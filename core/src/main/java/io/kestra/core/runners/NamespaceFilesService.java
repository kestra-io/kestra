package io.kestra.core.runners;

import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Singleton
@Slf4j
public class NamespaceFilesService {
    @Inject
    private StorageInterface storageInterface;

    public List<URI> inject(RunContext runContext, String tenantId, String namespace, Path basePath, NamespaceFiles namespaceFiles) throws Exception {
        if (!namespaceFiles.getEnabled()) {
            return Collections.emptyList();
        }

        List<URI> list = recursiveList(tenantId, namespace, null);

        list = list
            .stream()
            .filter(throwPredicate(f -> {
                var file = f.getPath();

                if (namespaceFiles.getExclude() != null) {
                    boolean b = match(runContext.render(namespaceFiles.getExclude()), file);

                    if (b) {
                        return false;
                    }
                }

                if (namespaceFiles.getInclude() != null) {
                    boolean b = match(namespaceFiles.getInclude(), file);

                    if (!b) {
                        return false;
                    }
                }

                return true;
            }))
            .collect(Collectors.toList());

        copy(tenantId, namespace, basePath, list);

        return list;
    }

    private URI uri(String namespace, @Nullable URI path) {
        return URI.create(StorageContext.namespaceFilePrefix(namespace) + Optional.ofNullable(path)
            .map(URI::getPath)
            .orElse("")
        );
    }

    public List<URI> recursiveList(String tenantId, String namespace, @Nullable URI path) throws IOException {
        return this.recursiveList(tenantId, namespace, path, false);
    }

    public List<URI> recursiveList(String tenantId, String namespace, @Nullable URI path, boolean includeDirectoryEntries) throws IOException {
        return storageInterface.allByPrefix(tenantId, URI.create(this.uri(namespace, path) + "/"), includeDirectoryEntries)
            // We get rid of Kestra schema as we want to work on a folder-like basis
            .stream().map(URI::getPath)
            .map(URI::create)
            .map(uri -> URI.create("/" + this.uri(namespace, null).relativize(uri)))
            .toList();
    }

    public boolean delete(String tenantId, String namespace, URI path) throws IOException {
        return storageInterface.delete(tenantId, this.uri(namespace, path));
    }

    public void createFile(String tenantId, String namespace, URI path, InputStream inputStream) throws IOException {
        storageInterface.put(tenantId, this.uri(namespace, path), inputStream);
    }

    public void createDirectory(String tenantId, String namespace, URI path) throws IOException {
        storageInterface.createDirectory(tenantId, this.uri(namespace, path));
    }

    private static boolean match(List<String> patterns, String file) {
        return patterns
            .stream()
            .anyMatch(s -> FileSystems
                .getDefault()
                .getPathMatcher("glob:" + (s.matches("\\w+[\\s\\S]*") ? "**/" + s : s))
                .matches(Paths.get(file))
            );
    }

    public InputStream content(String tenantId, String namespace, URI path) throws IOException {
        return storageInterface.get(tenantId, uri(namespace, path));
    }

    private void copy(String tenantId, String namespace, Path basePath, List<URI> files) throws IOException {
        files
            .forEach(throwConsumer(f -> {
                Path destination = Paths.get(basePath.toString(), f.getPath());

                if (!destination.getParent().toFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    destination.getParent().toFile().mkdirs();
                }

                try (InputStream inputStream = this.content(tenantId, namespace, f)) {
                    Files.copy(inputStream, destination, REPLACE_EXISTING);
                }
            }));
    }
}
