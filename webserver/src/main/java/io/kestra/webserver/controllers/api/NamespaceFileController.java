package io.kestra.webserver.controllers.api;

import io.kestra.core.services.FlowService;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.Rethrow;
import io.kestra.core.utils.WindowsUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Validated
@Controller("/api/v1/namespaces")
public class NamespaceFileController {
    public static final String FLOWS_FOLDER = "_flows";
    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;
    @Inject
    private FlowService flowService;

    private static final String DELIMITER_SLASH = "/";


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/search")
    @Operation(tags = {"Files"}, summary = "Find files which path contain the given string in their URI")
    public List<String> search(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The string the file path should contain") @QueryValue String q
    ) throws IOException, URISyntaxException {
        URI baseNamespaceFilesUri = NamespaceFile.of(namespace).uri();
        return storageInterface.allByPrefix(tenantService.resolveTenant(), namespace, baseNamespaceFilesUri, false).stream()
            .map(storageUri -> "/" + baseNamespaceFilesUri.relativize(storageUri).getPath())
            .filter(path -> path.contains(q)).toList();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Get namespace file content")
    public StreamedFile file(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        InputStream fileHandler = storageInterface.get(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace, encodedPath).uri());
        return new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/stats")
    @Operation(tags = {"Files"}, summary = "Get namespace file stats such as size, creation & modification dates and type")
    public FileAttributes stats(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        // if stats is performed upon namespace root, and it doesn't exist yet, we create it
        if (path == null || path.isEmpty()) {
            if(!storageInterface.exists(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).uri())) {
                storageInterface.createDirectory(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).uri());
            }
            return storageInterface.getAttributes(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).uri());
        }

        return storageInterface.getAttributes(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace, encodedPath).uri());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/directory")
    @Operation(tags = {"Files"}, summary = "List directory content")
    public List<FileAttributes> list(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        NamespaceFile namespaceFile = NamespaceFile.of(namespace, encodedPath);

        if (namespaceFile.isRootDirectory() && !storageInterface.exists(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).uri())) {
            storageInterface.createDirectory(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).uri());
            return Collections.emptyList();
        }

        return storageInterface.list(tenantService.resolveTenant(), namespace, namespaceFile.uri());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files/directory")
    @Operation(tags = {"Files"}, summary = "Create a directory")
    public void createDirectory(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        storageInterface.createDirectory(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace, encodedPath).uri());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Files"}, summary = "Create a file")
    public void createFile(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue String path,
        @Part CompletedFileUpload fileContent
    ) throws IOException, URISyntaxException {
        String tenantId = tenantService.resolveTenant();
        if (fileContent.getFilename().toLowerCase().endsWith(".zip")) {
            try (ZipInputStream archive = new ZipInputStream(fileContent.getInputStream())) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    try (BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(archive.readAllBytes()))) {
                        putNamespaceFile(tenantId, namespace, URI.create("/" + entry.getName()), inputStream);
                    }
                }
            }
        } else {
            try (BufferedInputStream inputStream = new BufferedInputStream(fileContent.getInputStream()) {
                // Done to bypass the wrong available() output of the CompletedFileUpload InputStream
                @Override
                public synchronized int available() {
                    return (int) fileContent.getSize();
                }
            }) {
                putNamespaceFile(tenantId, namespace, new URI(URLEncoder.encode(path, StandardCharsets.UTF_8)), inputStream);
            }
        }
    }

    private void putNamespaceFile(String tenantId, String namespace, URI path, BufferedInputStream inputStream) throws IOException {
        String filePath = path.getPath();
        if (filePath.startsWith(DELIMITER_SLASH)) {
            filePath = filePath.substring(1);
        }
        String[] filePaths = filePath.split(DELIMITER_SLASH);
        if (filePaths.length != 0 && filePaths[0].startsWith(FLOWS_FOLDER)) {
            if(filePaths.length != 2) {
                throw new IllegalArgumentException("Invalid flow file path: " + filePath);
            }

            String flowSource = new String(inputStream.readAllBytes());
            flowSource = flowSource.replaceFirst("(?m)^namespace: .*$", "namespace: " + namespace);
            this.importFlow(tenantId, flowSource);
            return;
        }
        storageInterface.put(tenantId, namespace, NamespaceFile.of(namespace, path).uri(), inputStream);
    }

    protected void importFlow(String tenantId, String source) {
        flowService.importFlow(tenantId, source);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/export", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Export namespace files as a ZIP")
    public HttpResponse<byte[]> export(
        @Parameter(description = "The namespace id") @PathVariable String namespace
        ) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream archive = new ZipOutputStream(bos)) {

            URI baseNamespaceFilesUri = NamespaceFile.of(namespace).uri();
            String tenantId = tenantService.resolveTenant();
            storageInterface.allByPrefix(tenantId, namespace, baseNamespaceFilesUri, false).forEach(Rethrow.throwConsumer(uri -> {
                try (InputStream inputStream = storageInterface.get(tenantId, namespace, uri)) {
                    archive.putNextEntry(new ZipEntry(baseNamespaceFilesUri.relativize(uri).getPath()));
                    archive.write(inputStream.readAllBytes());
                    archive.closeEntry();
                }
            }));

            flowService.findByNamespaceWithSource(tenantId, namespace).forEach(Rethrow.throwConsumer(flowWithSource -> {
                try {
                    archive.putNextEntry(new ZipEntry(FLOWS_FOLDER + "/" + flowWithSource.getId() + ".yml"));
                    archive.write(flowWithSource.getSource().getBytes());
                    archive.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));

            archive.finish();

            return HttpResponse.ok(bos.toByteArray()).header("Content-Disposition", "attachment; filename=\"" + namespace + "_files.zip\"");
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Move a file or directory")
    public void move(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri to move from") @QueryValue URI from,
        @Parameter(description = "The internal storage uri to move to") @QueryValue URI to
    ) throws IOException, URISyntaxException {
        ensureWritableNamespaceFile(from);
        ensureWritableNamespaceFile(to);

        storageInterface.move(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace, from).uri(),NamespaceFile.of(namespace, to).uri());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Delete a file or directory")
    public void delete(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri of the file / directory to delete") @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));

        ensureWritableNamespaceFile(encodedPath);

        String pathWithoutScheme = encodedPath.getPath();

        List<String> allNamespaceFilesPaths = storageInterface.allByPrefix(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace).storagePath().toUri(), true)
            .stream()
            .map(uri -> NamespaceFile.of(namespace, uri).path(true).toString())
            .collect(Collectors.toCollection(ArrayList::new));

        if (allNamespaceFilesPaths.contains(pathWithoutScheme + "/")) {
            // the given path to delete is a directory
            pathWithoutScheme = pathWithoutScheme + "/";
        }

        while (!pathWithoutScheme.equals("/")) {
            String parentFolder = pathWithoutScheme.substring(0, pathWithoutScheme.lastIndexOf('/') + 1);
            if (parentFolder.equals("/")) {
                break;
            }
            List<String> filesInParentFolder = allNamespaceFilesPaths.stream().filter(p -> p.length() > parentFolder.length() && p.startsWith(parentFolder)).toList();
            // there is more than one file in this folder so we stop the cascade deletion there
            if (filesInParentFolder.size() > 1) {
                break;
            }
            allNamespaceFilesPaths.removeIf(filesInParentFolder::contains);
            pathWithoutScheme = parentFolder.endsWith("/") ? parentFolder.substring(0, parentFolder.length() - 1) : parentFolder;
        }
        storageInterface.delete(tenantService.resolveTenant(), namespace, NamespaceFile.of(namespace, Path.of(pathWithoutScheme)).uri());
    }

    private void forbiddenPathsGuard(URI path) {
        if (path == null) {
            return;
        }
        String filePath = path.getPath();
        if (filePath.startsWith(DELIMITER_SLASH)) {
            filePath = filePath.substring(1);
        }
        String[] splitPath = filePath.split(DELIMITER_SLASH);
        if(splitPath.length == 0) {
            return;
        }
        if (splitPath[0].startsWith(FLOWS_FOLDER)) {
            throw new IllegalArgumentException("Forbidden path: " + path.getPath());
        }
    }

    private void ensureWritableNamespaceFile(URI path) {
        forbiddenPathsGuard(path);
    }
}
