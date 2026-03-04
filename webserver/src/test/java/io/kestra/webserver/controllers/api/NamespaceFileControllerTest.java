package io.kestra.webserver.controllers.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.Subflow;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

@KestraTest
class NamespaceFileControllerTest {
    public static final String TENANT_ID = TenantService.MAIN_TENANT;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private NamespaceFactory namespaceFactory;

    @SuppressWarnings("unchecked")
    @Test
    void searchNamespaceFiles() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);

        namespaceStorage.putFile(Path.of("/file.txt"), new ByteArrayInputStream(new byte[0]));
        namespaceStorage.putFile(Path.of("/another_file.json"), new ByteArrayInputStream(new byte[0]));
        namespaceStorage.putFile(Path.of("/folder/file.txt"), new ByteArrayInputStream(new byte[0]));
        namespaceStorage.putFile(Path.of("/folder/some.yaml"), new ByteArrayInputStream(new byte[0]));
        namespaceStorage.putFile(Path.of("/folder/sub/script.py"), new ByteArrayInputStream(new byte[0]));

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/search?q=file"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/file.txt", "/another_file.json", "/folder/file.txt");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/search?q=file.txt"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/file.txt", "/folder/file.txt");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/search?q=folder"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/folder/file.txt", "/folder/some.yaml", "/folder/sub/script.py");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/search?q=.py"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/folder/sub/script.py");
    }

    @Test
    void getFileContent() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        String hw = "Hello World";
        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream(hw.getBytes()));
        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files?path=/test.txt"));
        assertThat(res).isEqualTo(hw);
    }

    @Test
    void getFileContentWithRevision() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        String content1 = "Hello World";
        String content2 = "Hello World 2";
        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream(content1.getBytes()));
        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream(content2.getBytes()));

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files?path=/test.txt&revision=1"));
        assertThat(res).isEqualTo(content1);

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files?path=/test.txt&revision=2"));
        assertThat(res).isEqualTo(content2);

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files?path=/test.txt"));
        assertThat(res).isEqualTo(content2);
    }

    @Test
    void getFileMetadatas() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        String hw = "Hello World";
        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream(hw.getBytes()));
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/stats?path=/test.txt"), TestFileAttributes.class);
        assertThat(res.getFileName()).isEqualTo("test.txt");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.File);
    }



    @Test
    void getRevisions() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream("Hello World".getBytes()));

        List<NamespaceFileRevision> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/revisions?path=/test.txt"), Argument.of(List.class, NamespaceFileRevision.class));
        assertThat(res).containsExactlyInAnyOrder(new NamespaceFileRevision(1));

        namespaceStorage.putFile(Path.of("/test.txt"), new ByteArrayInputStream("Hello World 2".getBytes()));

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/revisions?path=/test.txt"), Argument.of(List.class, NamespaceFileRevision.class));
        assertThat(res).containsExactlyInAnyOrder(new NamespaceFileRevision(1), new NamespaceFileRevision(2));
    }

    @Test
    void namespaceRootGetFileMetadatasWithoutPreCreation() {
        String namespace = TestsUtils.randomNamespace();
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/stats"), TestFileAttributes.class);
        assertThat(res.getFileName()).isEqualTo("_files");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void listNamespaceDirectoryFiles() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        String hw = "Hello World";
        namespaceStorage.putFile(Path.of("/test/test.txt"), new ByteArrayInputStream(hw.getBytes()));
        namespaceStorage.putFile(Path.of("/test/test2.txt"), new ByteArrayInputStream(hw.getBytes()));

        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/directory"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("test");

        res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/directory?path=/test"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("test.txt", "test2.txt");
    }

    @Test
    void listNamespaceDirectoryFilesNotExisting() {
        String namespace = TestsUtils.randomNamespace();
        // Root directory will be automatically created
        assertThat(storageInterface.exists(
            TENANT_ID, namespace, toNamespacedStorageUri(namespace, null))).isFalse();
        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/directory"), TestFileAttributes[].class));
        assertThat(storageInterface.exists(
            TENANT_ID, namespace, toNamespacedStorageUri(namespace, null))).isTrue();
        assertThat(res.size()).isEqualTo(0);

        HttpClientResponseException notFoundException = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/directory?path=/not_existing_directory"), TestFileAttributes[].class));
        assertThat(notFoundException.getMessage()).contains("Directory not found: /not_existing_directory");
    }

    @Test
    void createNamespaceDirectory() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files/directory?path=/test", null));
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files/directory?path=/_flows2", null));
        FileAttributes res = storageInterface.getAttributes(TENANT_ID, namespace, toNamespacedStorageUri(namespace, URI.create("/test")));
        assertThat(res.getFileName()).isEqualTo("test");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
        FileAttributes flows = storageInterface.getAttributes(TENANT_ID, namespace, toNamespacedStorageUri(namespace, URI.create("/_flows2")));
        assertThat(flows.getFileName()).isEqualTo("_flows2");
        assertThat(flows.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void createNamespaceDirectoryException() {
        String namespace = TestsUtils.randomNamespace();
    assertThrows(
        HttpClientResponseException.class,
        () ->
            client
                .toBlocking()
                .exchange(
                    HttpRequest.POST(
                        "/api/v1/main/namespaces/" + namespace + "/files/directory?path=/_flows",
                        null)));
    }

    @Test
    void createGetFileContent() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/test.txt", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertNamespaceGetFileContentContent(namespace, URI.create("/test.txt"), "Hello");
        MultipartBody flowBody = MultipartBody.builder()
            .addPart("fileContent", "_flowsFile", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/_flowsFile", flowBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertNamespaceGetFileContentContent(namespace, URI.create("/_flowsFile"), "Hello");
    }

    @Test
    void createGetFileContentFlowException() {
        String namespace = TestsUtils.randomNamespace();
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "_flows", "Hello".getBytes())
            .build();
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/_flows", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        ));
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml"})
    void createGetFileContent_AddFlow() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        String flowSource = flowRepository.findByIdWithSource(TENANT_ID, "io.kestra.tests", "task-flow").get().getSource();
        File temp = File.createTempFile("task-flow", ".yml");
        Files.write(temp.toPath(), flowSource.getBytes());

        assertThat(flowRepository.findByIdWithSource(TENANT_ID, namespace, "task-flow").isEmpty()).isTrue();

        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "task-flow.yml", temp)
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/_flows/task-flow.yml", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );

        assertThat(flowRepository.findByIdWithSource(TENANT_ID, namespace, "task-flow").get().getSource()).isEqualTo(flowSource.replaceFirst("(?m)^namespace: .*$", "namespace: " + namespace));

        assertThat(storageInterface.exists(TENANT_ID, namespace, toNamespacedStorageUri(namespace, URI.create("/_flows/task-flow.yml")))).isFalse();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml"})
    void createGetFileContent_ExtractZip() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        String namespaceToExport = "io.kestra.tests";

        namespaceStorage.putFile(Path.of("/file.txt"), new ByteArrayInputStream("file".getBytes()));
        namespaceStorage.putFile(Path.of("/another_file.txt"), new ByteArrayInputStream("another_file".getBytes()));
        namespaceStorage.putFile(Path.of("/folder/file.txt"), new ByteArrayInputStream("folder_file".getBytes()));
        storageInterface.createDirectory(TENANT_ID, namespace, toNamespacedStorageUri(namespaceToExport, URI.create("/empty_folder")));

        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespaceToExport + "/files/export"),
            Argument.of(byte[].class));
        File temp = File.createTempFile("files", ".zip");
        Files.write(temp.toPath(), zip);

        assertThat(flowRepository.findById(TENANT_ID, namespace, "task-flow").isEmpty()).isTrue();

        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "files.zip", temp)
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/files.zip", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );

        assertNamespaceGetFileContentContent(namespace, URI.create("/file.txt"), "file");
        assertNamespaceGetFileContentContent(namespace, URI.create("/another_file.txt"), "another_file");
        assertThat(storageInterface.exists(TENANT_ID, namespace, toNamespacedStorageUri(namespace, URI.create("/folder")))).isTrue();
        assertNamespaceGetFileContentContent(namespace, URI.create("/folder/file.txt"), "folder_file");
        // Highlights the fact that we currently don't export / import empty folders (would require adding a method to storages to also retrieve folders)
        assertThat(storageInterface.exists(TENANT_ID, namespace, toNamespacedStorageUri(namespace, URI.create("/empty_folder")))).isFalse();

        Flow retrievedFlow = flowRepository.findById(TENANT_ID, namespace, "task-flow").get();
        assertThat(retrievedFlow.getNamespace()).isEqualTo(namespace);
        assertThat(((Subflow) retrievedFlow.getTasks().getFirst()).getNamespace()).isEqualTo(namespaceToExport);
    }

    private void assertNamespaceGetFileContentContent(String namespace, URI fileUri, String expectedContent) throws IOException {
        InputStream inputStream = storageInterface.get(TENANT_ID, namespace, toNamespacedStorageUri(namespace, fileUri));
        String content = new String(inputStream.readAllBytes());
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void moveFileDirectory() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        namespaceStorage.createDirectory(Path.of("/test"));
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + namespace + "/files?from=/test&to=/foo", null));
        FileAttributes res = namespaceStorage.getFileMetadata(Path.of("/foo"));
        assertThat(res.getFileName()).isEqualTo("foo");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void deleteFileDirectory() throws IOException, URISyntaxException {
        String namespace = TestsUtils.randomNamespace();
        Namespace namespaceStorage = namespaceFactory.of(TENANT_ID, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/folder/file.txt"), new ByteArrayInputStream("Hello".getBytes()));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + namespace + "/files?path=/folder/file.txt", null));
        assertThat(namespaceStorage.exists(Path.of("/folder/file.txt"))).isFalse();
        // Zombie folders are deleted, but not the root folder
        assertThat(namespaceStorage.exists(Path.of("/folder"))).isFalse();
        assertThat(namespaceStorage.exists(null)).isTrue();

        namespaceStorage.putFile(Path.of("/folderWithMultipleFiles/file1.txt"), new ByteArrayInputStream("Hello".getBytes()));
        namespaceStorage.putFile(Path.of("/folderWithMultipleFiles/file2.txt"), new ByteArrayInputStream("Hello".getBytes()));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + namespace + "/files?path=/folderWithMultipleFiles/file1.txt", null));
        assertThat(namespaceStorage.exists(Path.of("/folderWithMultipleFiles/file1.txt"))).isFalse();
        assertThat(namespaceStorage.exists(Path.of("/folderWithMultipleFiles/file2.txt"))).isTrue();
        assertThat(namespaceStorage.exists(Path.of("/folderWithMultipleFiles"))).isTrue();
        assertThat(namespaceStorage.exists(Path.of("/"))).isTrue();

        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + namespace + "/files?path=/folderWithMultipleFiles", null));
        assertThat(namespaceStorage.exists(Path.of("/folderWithMultipleFiles/"))).isFalse();
        assertThat(namespaceStorage.exists(null)).isTrue();
    }

    @Test
    void forbiddenPaths() {
        String namespace = TestsUtils.randomNamespace();
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files?path=/_flows/test.yml")));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/stats?path=/_flows/test.yml"), TestFileAttributes.class));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + namespace + "/files/directory?path=/_flows"), TestFileAttributes[].class));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + namespace + "/files?from=/_flows/test&to=/foo", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + namespace + "/files?from=/foo&to=/_flows/test", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + namespace + "/files?path=/_flows/test.txt", null)));
    }

    private void assertForbiddenErrorThrown(Executable executable) {
        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, executable);
        assertThat(httpClientResponseException.getMessage()).startsWith("Illegal argument: Forbidden path: ");
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return NamespaceFile.of(namespace, relativePath).storagePath().toUri();
    }

    @Getter
    @AllArgsConstructor
    public static class TestFileAttributes implements FileAttributes {
        String fileName;
        long lastModifiedTime;
        long creationTime;
        FileType type;
        long size;
        Map<String, String> metadata;
    }
}
