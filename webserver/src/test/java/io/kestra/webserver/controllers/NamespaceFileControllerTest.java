package io.kestra.webserver.controllers;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

@MicronautTest
class NamespaceFileControllerTest {
    private static final String NAMESPACE = "io.namespace";
    private static final String GETTING_STARTED_CONTENT;

    static {
        URL resource = NamespaceFileControllerTest.class.getResource("/static/getting-started.md");
        try {
            GETTING_STARTED_CONTENT = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset()).replace("${namespace}", NAMESPACE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @BeforeEach
    public void init() throws IOException {
        storageInterface.delete(null, toNamespacedStorageUri(NAMESPACE, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void search() throws IOException {
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/file.txt")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/another_file.json")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/file.txt")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/some.yaml")), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/folder/sub/script.py")), new ByteArrayInputStream(new byte[0]));

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=file"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res), containsInAnyOrder("/file.txt", "/another_file.json", "/folder/file.txt"));

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=file.txt"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res), containsInAnyOrder("/file.txt", "/folder/file.txt"));

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=folder"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res), containsInAnyOrder("/folder/file.txt", "/folder/some.yaml", "/folder/sub/script.py"));

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/search?q=.py"));
        assertThat((Iterable<String>) JacksonMapper.toObject(res), containsInAnyOrder("/folder/sub/script.py"));
    }

    @Test
    void file() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files?path=/test.txt"));
        assertThat(res, is(hw));
    }

    @Test
    void gettingStarted() {
        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files?path=/getting-started.md"));
        assertThat(res, is(GETTING_STARTED_CONTENT));
        assertThat(GETTING_STARTED_CONTENT, containsString("namespace: " + NAMESPACE));
    }

    @Test
    void stats() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats?path=/test.txt"), TestFileAttributes.class);
        assertThat(res.getFileName(), is("test.txt"));
        assertThat(res.getType(), is(FileAttributes.FileType.File));
        assertThat(res.isReadOnly(), is(false));
    }

    @Test
    void namespaceRootStatsWithoutPreCreation() {
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats"), TestFileAttributes.class);
        assertThat(res.getFileName(), is("_files"));
        assertThat(res.getType(), is(FileAttributes.FileType.Directory));
    }

    @Test
    void gettingStartedStats() {
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats?path=/getting-started.md"), TestFileAttributes.class);
        assertThat(res.getFileName(), is("getting-started.md"));
        assertThat(res.getType(), is(FileAttributes.FileType.File));
        assertThat(res.isReadOnly(), is(true));
        assertThat(res.getSize(), is((long) GETTING_STARTED_CONTENT.length()));
    }

    @Test
    void list() throws IOException {
        String hw = "Hello World";
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        storageInterface.put(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test/test2.txt")), new ByteArrayInputStream(hw.getBytes()));

        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList(), Matchers.containsInAnyOrder("getting-started.md", "test"));
        assertThat(res.stream().filter(fileAttributes -> fileAttributes.getFileName().equals("getting-started.md")).findFirst().get().isReadOnly(), is(true));

        res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/test"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList(), Matchers.containsInAnyOrder("test.txt", "test2.txt"));
    }

    @Test
    void createDirectory() throws IOException {
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/test", null));
        FileAttributes res = storageInterface.getAttributes(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test")));
        assertThat(res.getFileName(), is("test"));
        assertThat(res.getType(), is(FileAttributes.FileType.Directory));
    }

    @Test
    void createFile() throws IOException {
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/test.txt", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        InputStream inputStream = storageInterface.get(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test.txt")));
        String content = new String(inputStream.readAllBytes());
        assertThat(content, is("Hello"));
    }

    @Test
    void modifyGettingStarted_ShouldNotWork() {
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();

        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/getting-started.md", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                )
        );
        assertThat(exception.getMessage(), is("Illegal argument: 'getting-started.md' file is read-only"));

        assertThat(storageInterface.exists(null, toNamespacedStorageUri(NAMESPACE, URI.create("/getting-started.md"))), is(false));

        gettingStarted();
    }

    @Test
    void move() throws IOException {
        storageInterface.createDirectory(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test")));
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/test&to=/foo", null));
        FileAttributes res = storageInterface.getAttributes(null, toNamespacedStorageUri(NAMESPACE, URI.create("/foo")));
        assertThat(res.getFileName(), is("foo"));
        assertThat(res.getType(), is(FileAttributes.FileType.Directory));
    }

    @Test
    void moveGettingStarted_ShouldNotWork() {
        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/getting-started.md&to=/my-getting-started.md", null))
        );
        assertThat(exception.getMessage(), is("Illegal argument: 'getting-started.md' file is read-only"));

        assertThat(storageInterface.exists(null, toNamespacedStorageUri(NAMESPACE, URI.create("/my-getting-started.md"))), is(false));

        gettingStarted();
    }

    @Test
    void delete() throws IOException {
        storageInterface.createDirectory(null, toNamespacedStorageUri("namespace", URI.create("/test")));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/test", null));
        boolean res = storageInterface.exists(null, toNamespacedStorageUri(NAMESPACE, URI.create("/test")));
        assertThat(res, is(false));
    }

    @Test
    void deleteGettingStarted_ShouldNotWork() {
        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/getting-started.md", null))
        );
        assertThat(exception.getMessage(), is("Illegal argument: 'getting-started.md' file is read-only"));
    }

    @Test
    void forbiddenPaths() {
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/test.yml")));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/stats?path=/_flows/test.yml"), TestFileAttributes.class));
        assertForbiddenErrorThrown(() -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/_flows"), TestFileAttributes[].class));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files/directory?path=/_flows/test", null)));
        assertForbiddenErrorThrown(() -> {
            MultipartBody body = MultipartBody.builder()
                .addPart("fileContent", "test.txt", "Hello".getBytes())
                .build();
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/test.txt", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
        });
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/_flows/test&to=/foo", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/files?from=/foo&to=/_flows/test", null)));
        assertForbiddenErrorThrown(() -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/files?path=/_flows/test.txt", null)));
    }

    private void assertForbiddenErrorThrown(Executable executable) {
        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, executable);
        assertThat(httpClientResponseException.getMessage(), startsWith("Illegal argument: Forbidden path: "));
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return URI.create(storageInterface.namespaceFilePrefix(namespace) + Optional.ofNullable(relativePath).map(URI::getPath).orElse(""));
    }

    @Getter
    @AllArgsConstructor
    public static class TestFileAttributes implements FileAttributes {
        String fileName;
        long lastModifiedTime;
        long creationTime;
        FileType type;
        long size;
        boolean readOnly;
    }
}