package io.kestra.core.storages;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageNamespaceFileBackendTest {

    private static final String TENANT = "main";
    private static final String NAMESPACE = "io.kestra.tests";

    @Test
    void shouldUseNamespaceFileUriWhenAccessingStorage() throws IOException {
        // A URI built through java.nio.file.Path carries the drive letter on Windows, so the write and read paths diverged.
        StorageInterface storage = mock(StorageInterface.class);
        FileAttributes attributes = mock(FileAttributes.class);
        when(storage.getAttributes(any(), any(), any())).thenReturn(attributes);
        StorageNamespaceFileBackend backend = new StorageNamespaceFileBackend(storage);
        NamespaceFile file = NamespaceFile.of(NAMESPACE, "/lib/a b.txt", 2);
        NamespaceFile directory = NamespaceFile.of(NAMESPACE, "/lib/", 1);

        backend.put(TENANT, file, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
        backend.get(TENANT, file);
        backend.exists(TENANT, file);
        backend.delete(TENANT, file);
        backend.createDirectory(TENANT, directory);

        verify(storage).put(eq(TENANT), eq(NAMESPACE), eq(file.uri()), any(InputStream.class));
        verify(storage).getAttributes(TENANT, NAMESPACE, file.uri());
        verify(storage).get(TENANT, NAMESPACE, file.uri());
        verify(storage).exists(TENANT, NAMESPACE, file.uri());
        verify(storage).delete(TENANT, NAMESPACE, file.uri());
        verify(storage).createDirectory(TENANT, NAMESPACE, directory.uri());
    }
}
