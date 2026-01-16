package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.*;
import io.kestra.core.tenant.TenantService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpHeaders;
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
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Slf4j
@Validated
@Controller("/api/v1/{tenant}/namespaces")
public class NamespaceFileController {
    public static final String FLOWS_FOLDER = "_flows";
    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;
    @Inject
    private FlowService flowService;
    @Inject
    private NamespaceFactory namespaceFactory;
    @Inject
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;

    private final List<Pattern> forbiddenPathPatterns = List.of(
        Pattern.compile("/" + FLOWS_FOLDER + "(/.*)?$")
    );

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/search")
    @Operation(tags = {"Files"}, summary = "Find files which path contain the given string in their URI")
    public List<String> searchNamespaceFiles(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The string the file path should contain") @QueryValue String q
    ) throws IOException {
        return namespaceFactory.of(tenantService.resolveTenant(), namespace, storageInterface).all(q).stream().map(namespaceFile -> namespaceFile.path(true).toString()).toList();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Get namespace file content")
    public HttpResponse<StreamedFile> getFileContent(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue String path,
        @Nullable @Parameter(description = "The revision, if not provided, the latest revision will be returned") @QueryValue Integer revision
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        Path filePath = Optional.ofNullable(encodedPath).map(URI::getPath).map(Path::of).orElseThrow();
        InputStream fileContent = namespaceFactory.of(tenantService.resolveTenant(), namespace, storageInterface)
            .getFileContent(filePath, revision);
        return HttpResponse.ok(new StreamedFile(fileContent, MediaType.APPLICATION_OCTET_STREAM_TYPE)).header(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/stats")
    @Operation(tags = {"Files"}, summary = "Get namespace file stats such as size, creation & modification dates and type")
    public FileAttributes getFileMetadatas(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        // if stats is performed upon namespace root, and it doesn't exist yet, we create it
        Namespace namespaceStorage = namespaceFactory.of(tenantService.resolveTenant(), namespace, storageInterface);
        Path rootPath = Path.of("/");
        if (path == null || path.isEmpty()) {
            if (!namespaceStorage.exists(rootPath)) {
                namespaceStorage.createDirectory(rootPath);
            }
            return namespaceStorage.getFileMetadata(rootPath);
        }

        return namespaceStorage.getFileMetadata(Path.of(encodedPath.getPath()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/revisions")
    @Operation(tags = {"Files"}, summary = "Get namespace file revisions")
    public List<NamespaceFileRevision> getFileRevisions(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        encodedPath = Optional.ofNullable(encodedPath).orElse(URI.create("/"));

        ArrayListTotal<NamespaceFileMetadata> namespaceFileMetadata = namespaceFileMetadataRepository.find(Pageable.UNPAGED, tenantService.resolveTenant(), List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
            QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.EQUALS).value(encodedPath.getPath()).build()
        ), true, FetchVersion.ALL);

        if (namespaceFileMetadata.stream()
            .filter(NamespaceFileMetadata::isLast)
            .map(NamespaceFileMetadata::isDeleted).findFirst()
            .orElse(true)) {
            throw new FileNotFoundException("File not found: " + encodedPath.getPath());
        }

        return namespaceFileMetadata.map(metadata -> new NamespaceFileRevision(metadata.getVersion()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/directory")
    @Operation(tags = {"Files"}, summary = "List directory content")
    public List<FileAttributes> listNamespaceDirectoryFiles(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        Namespace namespaceStorage = namespaceFactory.of(tenantService.resolveTenant(), namespace, storageInterface);
        Path dirPath = Path.of(Optional.ofNullable(encodedPath).map(URI::getPath).orElse("/"));

        if (dirPath.toString().equals("/") && !namespaceStorage.exists(dirPath)) {
            namespaceStorage.createDirectory(dirPath);
        } else if (!namespaceStorage.exists(dirPath)) {
            throw new FileNotFoundException("Directory not found: " + dirPath);
        }

        return namespaceStorage.children(dirPath.toString(), false).stream()
            .map(namespaceFileMetadata -> (FileAttributes) new NamespaceFileAttributes(namespaceFileMetadata))
            .toList();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files/directory")
    @Operation(tags = {"Files"}, summary = "Create a directory")
    public void createNamespaceDirectory(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @Nullable @QueryValue String path
    ) throws IOException, URISyntaxException {
        URI encodedPath = null;
        if (path != null) {
            encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        }
        forbiddenPathsGuard(encodedPath);

        Namespace namespaceStorage = namespaceFactory.of(tenantService.resolveTenant(), namespace, storageInterface);
        namespaceStorage.createDirectory(Optional.ofNullable(encodedPath).map(URI::getPath).map(Path::of).orElse(Path.of("/")));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}/files", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Files"}, summary = "Create a file")
    public void createNamespaceFile(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri") @QueryValue String path,
        @Parameter(description = "The file to upload") @Part CompletedFileUpload fileContent
    ) throws Exception {
        innerCreateNamespaceFile(namespace, path, fileContent);
    }

    protected List<NamespaceFile> innerCreateNamespaceFile(String namespace, String path, CompletedFileUpload fileContent) throws Exception {
        String tenantId = tenantService.resolveTenant();
        List<NamespaceFile> createdFiles = new ArrayList<>();
        if (fileContent.getFilename().toLowerCase().endsWith(".zip")) {
            try (ZipInputStream archive = new ZipInputStream(fileContent.getInputStream())) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    try (BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(archive.readAllBytes()))) {
                        createdFiles.addAll(putNamespaceFile(tenantId, namespace, URI.create("/" + entry.getName()), inputStream));
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
                createdFiles.addAll(putNamespaceFile(tenantId, namespace, new URI(URLEncoder.encode(path, StandardCharsets.UTF_8)), inputStream));
            }
        }

        return createdFiles;
    }

    private List<NamespaceFile> putNamespaceFile(String tenantId, String namespace, URI path, BufferedInputStream inputStream) throws Exception {
        String filePath = path.getPath();
        if (filePath.matches("/" + FLOWS_FOLDER + "/.*")) {
            if (filePath.split("/").length != 3) {
                throw new IllegalArgumentException("Invalid flow file path: " + filePath);
            }

            String flowSource = new String(inputStream.readAllBytes());
            flowSource = flowSource.replaceFirst("(?m)^namespace: .*$", "namespace: " + namespace);
            this.importFlow(tenantId, flowSource);
            return Collections.emptyList();
        }
        forbiddenPathsGuard(path);

        Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
        return namespaceStorage.putFile(Path.of(path.getPath()), inputStream);
    }

    protected void importFlow(String tenantId, String source) throws FlowProcessingException {
        flowService.importFlow(tenantId, source);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/files/export", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Files"}, summary = "Export namespace files as a ZIP")
    public HttpResponse<byte[]> exportNamespaceFiles(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream archive = new ZipOutputStream(bos)) {

            String tenantId = tenantService.resolveTenant();

            Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
            List<NamespaceFileMetadata> allNsFiles = namespaceStorage.children("/", true);
            allNsFiles.stream()
                .filter(Predicate.not(NamespaceFileMetadata::isDirectory))
                .map(NamespaceFileMetadata::getPath)
                .forEach(throwConsumer(path -> {
                    try (InputStream inputStream = namespaceStorage.getFileContent(Path.of(path))) {
                        archive.putNextEntry(new ZipEntry(path.substring(1))); // remove leading slash
                        archive.write(inputStream.readAllBytes());
                        archive.closeEntry();
                    }
                }));

            flowService.findByNamespaceWithSource(tenantId, namespace).forEach(throwConsumer(flowWithSource -> {
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
    public void moveFileDirectory(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri to move from") @QueryValue URI from,
        @Parameter(description = "The internal storage uri to move to") @QueryValue URI to
    ) throws Exception {
        innerMoveFileDirectory(namespace, from, to);
    }

    protected List<Pair<NamespaceFile, NamespaceFile>> innerMoveFileDirectory(String namespace, URI from, URI to) throws Exception {
        ensureWritableNamespaceFile(from);
        ensureWritableNamespaceFile(to);

        String tenantId = tenantService.resolveTenant();

        Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
        return namespaceStorage.move(Path.of(from.getPath()), Path.of(to.getPath()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/files")
    @Operation(tags = {"Files"}, summary = "Delete a file or directory")
    public void deleteFileDirectory(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The internal storage uri of the file / directory to delete") @QueryValue String path
    ) throws IOException, URISyntaxException {
        innerDeleteFileDirectory(namespace, path);
    }

    protected List<NamespaceFile> innerDeleteFileDirectory(String namespace, String path) throws URISyntaxException, IOException {
        URI encodedPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        encodedPath = new URI(URLEncoder.encode(path, StandardCharsets.UTF_8));
        ensureWritableNamespaceFile(encodedPath);

        String pathWithoutScheme = encodedPath.getPath();

        String tenantId = tenantService.resolveTenant();

        String zombieAwarePathToDelete = pathWithoutScheme;
        String parentPathToCheck = NamespaceFileMetadata.parentPath(zombieAwarePathToDelete);
        while (parentPathToCheck != null && !parentPathToCheck.equals("/") && namespaceFileMetadataRepository.find(Pageable.from(1, 2), tenantService.resolveTenant(), List.of(
            QueryFilter.builder().field(QueryFilter.Field.PARENT_PATH).operation(QueryFilter.Op.EQUALS).value(parentPathToCheck).build()
        ), false).size() == 1) {
            zombieAwarePathToDelete = parentPathToCheck;
            parentPathToCheck = NamespaceFileMetadata.parentPath(parentPathToCheck);
        }

        Namespace namespaceStorage = namespaceFactory.of(tenantId, namespace, storageInterface);
        return namespaceStorage.delete(Path.of(zombieAwarePathToDelete));
    }

    private void forbiddenPathsGuard(URI path) {
        if (path == null) {
            return;
        }

        if (forbiddenPathPatterns.stream().anyMatch(pattern -> pattern.matcher(path.getPath()).matches())) {
            throw new IllegalArgumentException("Forbidden path: " + path.getPath());
        }
    }

    private void ensureWritableNamespaceFile(URI path) {
        forbiddenPathsGuard(path);
    }
}
