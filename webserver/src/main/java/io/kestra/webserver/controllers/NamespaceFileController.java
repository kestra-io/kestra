package io.kestra.webserver.controllers;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
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
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Validated
@Controller("/api/v1/namespaces")
public class NamespaceFileController {
    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Get namespace file content")
    public StreamedFile file(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        InputStream fileHandler = storageInterface.get(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
        return new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/stats", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Files"}, summary = "Get namespace file stats such as size, creation & modification dates and type")
    public FileAttributes stats(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue URI path
    ) throws IOException, URISyntaxException {
        try {
            return storageInterface.getAttributes(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/directory", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Files"}, summary = "List directory content")
    public List<FileAttributes> list(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue URI path
    ) throws IOException, URISyntaxException {
        return storageInterface.list(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
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
        storageInterface.move(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, from), toNamespacedStorageUri(namespace, to));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Delete a file or directory")
    public void delete(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri of the file / directory to delete") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        storageInterface.delete(tenantService.resolveTenant(), toNamespacedStorageUri(namespace, path));
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return URI.create(storageInterface.namespaceFilePrefix(namespace) + Optional.ofNullable(relativePath).map(URI::getPath).orElse(""));
    }
}
