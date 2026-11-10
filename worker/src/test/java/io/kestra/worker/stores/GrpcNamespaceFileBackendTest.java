package io.kestra.worker.stores;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.NamespaceFileContentServiceGrpc;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.storages.InternalNamespace;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class GrpcNamespaceFileBackendTest extends AbstractGrpcMetaStoreTest {

    private static final String TENANT = "main";

    // Larger than a single chunk, so both directions actually exercise the streaming.
    private static final String LARGE_CONTENT = "x".repeat(2 * 1024 * 1024 + 17);

    @Inject
    StorageInterface storage;

    @Inject
    NamespaceFileMetadataStateStore stateStore;

    @Inject
    NamespaceFileContentServiceGrpc.NamespaceFileContentServiceStub stub;

    @Inject
    NamespaceFileContentServiceGrpc.NamespaceFileContentServiceBlockingStub blockingStub;

    private GrpcNamespaceFileBackend backend;

    @Override
    protected void initClientStore() {
        backend = new GrpcNamespaceFileBackend(stub, blockingStub, clientWorkerInfo());
    }

    @Test
    void shouldWriteAFileThatTheClusterStorageServes() throws Exception {
        String namespace = namespace();

        workerNamespace(namespace).putFile(Path.of("/scripts/large.txt"), content(LARGE_CONTENT));

        assertThat(read(clusterNamespace(namespace).getFileContent(Path.of("/scripts/large.txt")))).isEqualTo(LARGE_CONTENT);
        assertThat(clusterNamespace(namespace).getFileMetadata(Path.of("/scripts/large.txt")).getSize()).isEqualTo(LARGE_CONTENT.length());
    }

    @Test
    void shouldReadTheLatestRevisionOfAFileWrittenOnTheClusterStorage() throws Exception {
        String namespace = namespace();
        clusterNamespace(namespace).putFile(Path.of("/large.txt"), content("first"));
        clusterNamespace(namespace).putFile(Path.of("/large.txt"), content(LARGE_CONTENT));

        assertThat(read(workerNamespace(namespace).getFileContent(Path.of("/large.txt")))).isEqualTo(LARGE_CONTENT);
        assertThat(read(workerNamespace(namespace).getFileContent(Path.of("/large.txt"), 1))).isEqualTo("first");
    }

    @Test
    void shouldMoveAndDeleteFilesOfTheClusterStorage() throws Exception {
        String namespace = namespace();
        clusterNamespace(namespace).putFile(Path.of("/from/file.txt"), content("moved"));
        Namespace workerNamespace = workerNamespace(namespace);

        workerNamespace.move(Path.of("/from"), Path.of("/to"));

        assertThat(read(clusterNamespace(namespace).getFileContent(Path.of("/to/file.txt")))).isEqualTo("moved");
        assertThat(storage.exists(TENANT, namespace, NamespaceFile.of(namespace, Path.of("from/file.txt")).storagePath().toUri())).isFalse();

        workerNamespace.delete(Path.of("/to"));

        assertThat(clusterNamespace(namespace).exists(Path.of("/to/file.txt"))).isFalse();
    }

    @Test
    void shouldFailWithFileNotFoundWhenTheObjectHasNoContent() {
        assertThatThrownBy(() -> backend.get(TENANT, NamespaceFile.of(namespace(), Path.of("missing.txt"))))
            .isInstanceOf(FileNotFoundException.class);
    }

    private Namespace workerNamespace(String namespace) {
        return new InternalNamespace(TENANT, namespace, backend, stateStore);
    }

    private Namespace clusterNamespace(String namespace) {
        return new InternalNamespace(TENANT, namespace, storage, stateStore);
    }

    private static InputStream content(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream content) throws Exception {
        try (content) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String namespace() {
        return "io.kestra." + IdUtils.create().toLowerCase();
    }
}
