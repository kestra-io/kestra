package io.kestra.webserver.controllers.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
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
import java.nio.file.Files;
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
    private static final String NAMESPACE = "io.namespace";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @AfterEach
    public void clean() throws IOException {
        storageInterface.delete(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchNamespaceFiles() throws IOException {
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/file.txt")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/another_file.json")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/file.txt")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/some.yaml")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/sub/script.py")), new ByteArrayInputStream(new byte[0]));

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=file"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/file.txt", "/another_file.json", "/folder/file.txt");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=file.txt"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/file.txt", "/folder/file.txt");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=folder"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/folder/file.txt", "/folder/some.yaml", "/folder/sub/script.py");

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=.py"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res)).containsExactlyInAnyOrder("/folder/sub/script.py");
    }

    @Test
    void getFileContent() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files?path=/test.txt"));
        assertThat(res).isEqualTo(hw);
    }

    @Test
    void getFileMetadatas() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats?path=/test.txt"), TestFileAttributes.class);
        assertThat(res.getFileName()).isEqualTo("test.txt");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.File);
    }

    @Test
    void namespaceRootGetFileMetadatasWithoutPreCreation() {
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats"), TestFileAttributes.class);
        assertThat(res.getFileName()).isEqualTo("_files");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void listNamespaceDirectoryFiles() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test/test2.txt")), new ByteArrayInputStream(hw.getBytes()));

        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("test");

        res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/test"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("test.txt", "test2.txt");
    }

    @Test
    void listNamespaceDirectoryFilesWithoutPreCreation() {
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null))).isEqualTo(false);
        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory"), TestFileAttributes[].class));
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null))).isEqualTo(true);
        assertThat(res.stream().map(FileAttributes::getFileName).count()).isEqualTo(0L);
    }

    @Test
    void createNamespaceDirectory() throws IOException {
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/test", null));
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/_flows2", null));
        FileAttributes res = storageInterface.getAttributes(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test")));
        assertThat(res.getFileName()).isEqualTo("test");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
        FileAttributes flows = storageInterface.getAttributes(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/_flows2")));
        assertThat(flows.getFileName()).isEqualTo("_flows2");
        assertThat(flows.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void createNamespaceDirectoryException() {
    assertThrows(
        HttpClientResponseException.class,
        () ->
            client
                .toBlocking()
                .exchange(
                    HttpRequest.POST(
                        "/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/_flows",
                        null)));
    }

    @Test
    void createGetFileContent() throws IOException {
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/test.txt", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertNamespaceGetFileContentContent(URI.create("/test.txt"), "Hello");
        MultipartBody flowBody = MultipartBody.builder()
            .addPart("fileContent", "_flowsFile", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flowsFile", flowBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertNamespaceGetFileContentContent(URI.create("/_flowsFile"), "Hello");
    }

    @Test
    void createGetFileContentFlowException() {
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "_flows", "Hello".getBytes())
            .build();
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        ));
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml"})
    void createGetFileContent_AddFlow() throws IOException {
        String flowSource = flowRepository.findByIdWithSource(null, "io.kestra.tests", "task-flow").get().getSource();
        File temp = File.createTempFile("task-flow", ".yml");
        Files.write(temp.toPath(), flowSource.getBytes());

        assertThat(flowRepository.findByIdWithSource(null, NAMESPACE, "task-flow").isEmpty()).isEqualTo(true);

        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "task-flow.yml", temp)
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/task-flow.yml", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );

        assertThat(flowRepository.findByIdWithSource(null, NAMESPACE, "task-flow").get().getSource()).isEqualTo(flowSource.replaceFirst("(?m)^namespace: .*$", "namespace: " + NAMESPACE));

        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/_flows/task-flow.yml")))).isEqualTo(false);
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml"})
    void createGetFileContent_ExtractZip() throws IOException {
        String namespaceToExport = "io.kestra.tests";

        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(namespaceToExport, URI.create("/file.txt")), new ByteArrayInputStream("file".getBytes()));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(namespaceToExport, URI.create("/another_file.txt")), new ByteArrayInputStream("another_file".getBytes()));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(namespaceToExport, URI.create("/folder/file.txt")), new ByteArrayInputStream("folder_file".getBytes()));
        storageInterface.createDirectory(null, NAMESPACE, toNamespacedStorageUri(namespaceToExport, URI.create("/empty_folder")));

        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + namespaceToExport + "/files/export"),
            Argument.of(byte[].class));
        File temp = File.createTempFile("files", ".zip");
        Files.write(temp.toPath(), zip);

        assertThat(flowRepository.findById(null, NAMESPACE, "task-flow").isEmpty()).isEqualTo(true);

        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "files.zip", temp)
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/files.zip", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );

        assertNamespaceGetFileContentContent(URI.create("/file.txt"), "file");
        assertNamespaceGetFileContentContent(URI.create("/another_file.txt"), "another_file");
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder")))).isEqualTo(true);
        assertNamespaceGetFileContentContent(URI.create("/folder/file.txt"), "folder_file");
        // Highlights the fact that we currently don't export / import empty folders (would require adding a method to storages to also retrieve folders)
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/empty_folder")))).isEqualTo(false);

        Flow retrievedFlow = flowRepository.findById(null, NAMESPACE, "task-flow").get();
        assertThat(retrievedFlow.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(((Subflow) retrievedFlow.getTasks().getFirst()).getNamespace()).isEqualTo(namespaceToExport);
    }

    private void assertNamespaceGetFileContentContent(URI fileUri, String expectedContent) throws IOException {
        InputStream inputStream = storageInterface.get(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, fileUri));
        String content = new String(inputStream.readAllBytes());
        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    void moveFileDirectory() throws IOException {
        storageInterface.createDirectory(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/test")));
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/test&to=/foo", null));
        FileAttributes res = storageInterface.getAttributes(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/foo")));
        assertThat(res.getFileName()).isEqualTo("foo");
        assertThat(res.getType()).isEqualTo(FileAttributes.FileType.Directory);
    }

    @Test
    void deleteFileDirectory() throws IOException {
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/file.txt")), new ByteArrayInputStream("Hello".getBytes()));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/folder/file.txt", null));
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/file.txt")))).isEqualTo(false);
        // Zombie folders are deleted, but not the root folder
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folder")))).isEqualTo(false);
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null))).isEqualTo(true);

        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles/file1.txt")), new ByteArrayInputStream("Hello".getBytes()));
        storageInterface.put(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles/file2.txt")), new ByteArrayInputStream("Hello".getBytes()));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/folderWithMultipleFiles/file1.txt", null));
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles/file1.txt")))).isEqualTo(false);
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles/file2.txt")))).isEqualTo(true);
        // Since there is still one file in the folder, it should not be deleted
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles")))).isEqualTo(true);
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null))).isEqualTo(true);

        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/folderWithMultipleFiles", null));
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, URI.create("/folderWithMultipleFiles/")))).isEqualTo(false);
        assertThat(storageInterface.exists(null, NAMESPACE, toNamespacedStorageUri(NAMESPACE, null))).isEqualTo(true);
    }

    @Test
    void forbiddenPaths() {
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/test.yml")));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats?path=/_flows/test.yml"), TestFileAttributes.class));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/_flows"), TestFileAttributes[].class));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/_flows/test&to=/foo", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/foo&to=/_flows/test", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/test.txt", null)));
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