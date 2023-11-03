package io.kestra.webserver.controllers;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@MicronautTest
class NamespaceFileControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void file() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("namespace", URI.create(prefix)));
        String hw = "Hello World";
        storageInterface.put(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/io.namespace/files?path=" + prefix + "/test.txt"));
        assertThat(res, is(hw));
    }

    @Test
    void stats() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("namespace", URI.create(prefix)));

        String hw = "Hello World";
        storageInterface.put(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        FileAttributes res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/io.namespace/files/stats?path=" + prefix + "/test.txt"), TestFileAttributes.class);
        assertThat(res.getFileName(), is("test.txt"));
        assertThat(res.getType(), is(FileAttributes.FileType.File));
    }

    @Test
    void list() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix)));

        String hw = "Hello World";
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test")));
        storageInterface.put(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test/test.txt")), new ByteArrayInputStream(hw.getBytes()));
        storageInterface.put(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test/test2.txt")), new ByteArrayInputStream(hw.getBytes()));
        List<FileAttributes> res = List.of(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/io.namespace/files/directory?path=" + prefix + "/test"), TestFileAttributes[].class));
        assertThat(res.stream().map(FileAttributes::getFileName).toList(), Matchers.containsInAnyOrder("test.txt", "test2.txt"));
    }

    @Test
    void createDirectory() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix)));

        client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/io.namespace/files/directory?path=" + prefix + "/test", null));
        FileAttributes res = storageInterface.getAttributes(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test")));
        assertThat(res.getFileName(), is("test"));
        assertThat(res.getType(), is(FileAttributes.FileType.Directory));
    }

    @Test
    void createFile() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix)));
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/io.namespace/files?path=" + prefix + "/test.txt", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        InputStream inputStream = storageInterface.get(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test.txt")));
        String content = new String(inputStream.readAllBytes());
        assertThat(content, is("Hello"));
    }

    @Test
    void move() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix)));

        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test")));
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/io.namespace/files?from=" + prefix + "/test&to=" + prefix + "/foo", null));
        FileAttributes res = storageInterface.getAttributes(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/foo")));
        assertThat(res.getFileName(), is("foo"));
        assertThat(res.getType(), is(FileAttributes.FileType.Directory));
    }

    @Test
    void delete() throws IOException {
        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("namespace", URI.create(prefix)));

        storageInterface.createDirectory(null, toNamespacedStorageUri("namespace", URI.create(prefix + "/test")));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/io.namespace/files?path=" + prefix + "/test", null));
        boolean res = storageInterface.exists(null, toNamespacedStorageUri("io.namespace", URI.create(prefix + "/test")));
        assertThat(res, is(false));
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
    }
}