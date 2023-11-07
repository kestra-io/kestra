package io.kestra.webserver.controllers;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.ImmutableFileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.Rethrow;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Validated
@Controller("/api/v1/namespaces")
public class NamespaceFileController {
    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;

    private final List<StaticFile> staticFiles;

    {
        try {
            staticFiles = List.of(
                new StaticFile(
                    "/getting-started.md",
                    "/static/getting-started.md"
                )
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Get namespace file content")
    public StreamedFile file(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        Optional<StaticFile> maybeStaticFile = staticFiles.stream().filter(staticFile -> path.getPath().equals(staticFile.getServedPath())).findFirst();
        InputStream fileHandler = maybeStaticFile
            .<InputStream>map(staticFile -> new ByteArrayInputStream(staticFile.getTemplatedContent(Map.of("namespace", namespace)).getBytes()))
            .orElseGet(Rethrow.throwSupplier(() -> storageInterface.get(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path))));

        return new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/stats", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Files"}, summary = "Get namespace file stats such as size, creation & modification dates and type")
    public FileAttributes stats(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue URI path
    ) throws IOException, URISyntaxException {
        // if stats is performed upon namespace root and it doesn't exist yet, we create it
        if (path == null) {
            if(!storageInterface.exists(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, null))) {
                storageInterface.createDirectory(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, null));
            }

            return storageInterface.getAttributes(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, null));
        }

        Optional<StaticFile> maybeStaticFile = staticFiles.stream().filter(staticFile -> path.getPath().equals(staticFile.getServedPath())).findFirst();
        return maybeStaticFile
            .map(staticFile -> staticFile.getStats(Map.of("namespace", namespace)))
            .orElseGet(Rethrow.throwSupplier(() -> storageInterface.getAttributes(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path))));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/directory", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Files"}, summary = "List directory content")
    public List<FileAttributes> list(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue URI path
    ) throws IOException, URISyntaxException {
        if (path == null) {
            path = URI.create("/");
        }
        String pathString = path.getPath();
        return Stream.concat(
            storageInterface.list(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path)).stream(),
            staticFiles.stream()
                .filter(staticFile -> staticFile.getServedPath().startsWith(pathString))
                .map(staticFile -> staticFile.getStats(Map.of("namespace", namespace)))
        ).toList();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files/directory")
    @Operation(tags = {"Files"}, summary = "Create a directory")
    public void createDirectory(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue URI path
    ) throws IOException, URISyntaxException {
        storageInterface.createDirectory(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Files"}, summary = "Create a file")
    public void createFile(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue URI path,
        @Part CompletedFileUpload fileContent
    ) throws IOException, URISyntaxException {
        ensureWritableFile(path);

        storageInterface.put(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path), new BufferedInputStream(fileContent.getInputStream()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Move a file or directory")
    public void move(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri to move from") @QueryValue URI from,
        @Parameter(description = "The internal storage uri to move to") @QueryValue URI to
    ) throws IOException, URISyntaxException {
        ensureWritableFile(from);
        ensureWritableFile(to);

        storageInterface.move(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, from), toNamespacedStorageUri(namespace, to));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Delete a file or directory")
    public void delete(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri of the file / directory to delete") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        ensureWritableFile(path);

        storageInterface.delete(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return URI.create(storageInterface.namespaceFilePrefix(namespace) + Optional.ofNullable(relativePath).map(URI::getPath).orElse("/"));
    }

    private void ensureWritableFile(URI path) {
        Optional<StaticFile> maybeStaticFile = staticFiles.stream().filter(staticFile -> path.getPath().equals(staticFile.getServedPath())).findFirst();
        if(maybeStaticFile.isPresent()) {
            throw new IllegalArgumentException("'" + maybeStaticFile.get().getServedPath().replaceFirst("^/", "") + "' file is read-only");
        }
    }

    @Value
    private static class StaticFile {
        String servedPath;
        String fileName;
        String rawContent;

        private StaticFile(String servedPath, String staticFilePath) throws IOException {
            this.servedPath = servedPath;

            int lastSlash = staticFilePath.lastIndexOf("/");
            this.fileName = lastSlash < 0 ? staticFilePath : staticFilePath.substring(lastSlash + 1);

            InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(staticFilePath));
            this.rawContent = new String(inputStream.readAllBytes());
            inputStream.close();
        }

        public FileAttributes getStats(Map<String, String> templatedStrings) {
            return new ImmutableFileAttributes(this.fileName, this.getTemplatedContent(templatedStrings).length());
        }

        public String getTemplatedContent(Map<String, String> templatedStrings) {
            String templatedContent = this.rawContent;
            for (Map.Entry<String, String> entry : templatedStrings.entrySet()) {
                templatedContent = templatedContent.replace("${" + entry.getKey() + "}", entry.getValue());
            }

            return templatedContent;
        }
    }
}
