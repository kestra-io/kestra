package io.kestra.storage.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.utils.IdUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageTest extends StorageTestSuite {
    // Launch test from StorageTestSuite

    @Test
    void putLongObjectName() throws URISyntaxException, IOException {
        String longObjectName = "/" + RandomStringUtils.insecure().nextAlphanumeric(260).toLowerCase();

        URI put = storageInterface.put(
            IdUtils.create(),
            null,
            new URI(longObjectName),
            new ByteArrayInputStream("Hello World".getBytes())
        );

        assertThat(put.getPath(), not(longObjectName));
        String suffix = put.getPath().substring(7); // we remove the random 5 char + '-'
        assertTrue(longObjectName.endsWith(suffix));
    }

    // GHSA-qw4v-6w32-xx9h: a Windows-style backslash traversal must not escape the storage
    // base directory. Before the fix, the guard ran before backslashes were converted to '/',
    // so this payload reached arbitrary host files (e.g. /etc/passwd).
    // %5C decodes to '\' in URI.getPath().
    @Test
    void shouldRejectBackslashParentTraversal() {
        URI backslashTraversal = URI.create(
            "kestra:///abc%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5C..%5Cetc%5Cpasswd"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> storageInterface.get(IdUtils.create(), null, backslashTraversal)
        );
    }

    // The classic forward-slash traversal must keep being rejected as well.
    @Test
    void shouldRejectForwardSlashParentTraversal() {
        URI traversal = URI.create("kestra:///abc/../../../../../../../../etc/passwd");

        assertThrows(
            IllegalArgumentException.class,
            () -> storageInterface.get(IdUtils.create(), null, traversal)
        );
    }

    // When the parent directory hierarchy cannot be created (here: a regular file occupies a
    // path segment), put must fail with a descriptive exception naming the offending path —
    // not the misleading FileNotFoundException that surfaced while File#mkdirs' boolean
    // result was ignored (see issue #17093).
    @Test
    void shouldFailWithDescriptiveErrorWhenParentDirectoryCannotBeCreated() throws URISyntaxException, IOException {
        // Given: a regular file at the path where the parent directory would be created
        String tenantId = IdUtils.create();
        storageInterface.put(
            tenantId,
            null,
            new URI("/parent-conflict/blocking"),
            new ByteArrayInputStream("i am a file, not a directory".getBytes())
        );

        // When: putting an object whose parent path traverses that regular file
        // Then: the failure names the conflicting path instead of a misleading "not found"
        FileAlreadyExistsException exception = assertThrows(
            FileAlreadyExistsException.class,
            () -> storageInterface.put(
                tenantId,
                null,
                new URI("/parent-conflict/blocking/child.ion"),
                new ByteArrayInputStream("Hello World".getBytes())
            )
        );
        assertTrue(exception.getMessage().contains("blocking"));
    }

    @Test
    void shouldMoveToDestinationWithUncreatedParentDirectory() throws URISyntaxException, IOException {
        String tenantId = IdUtils.create();
        storageInterface.put(tenantId, null, new URI("/input.csv"), new ByteArrayInputStream("data".getBytes()));

        storageInterface.move(tenantId, null, new URI("/input.csv"), new URI("/archive/2026/08/input.csv"));

        assertTrue(storageInterface.exists(tenantId, null, new URI("/archive/2026/08/input.csv")));
        assertFalse(storageInterface.exists(tenantId, null, new URI("/input.csv")));
    }

    @Test
    void shouldMoveObjectWithCompanionMetadata() throws URISyntaxException, IOException {
        String tenantId = IdUtils.create();
        storageInterface.put(
            tenantId,
            null,
            new URI("/source.csv"),
            new StorageObject(Map.of("someMetadata", "someValue"), new ByteArrayInputStream("data".getBytes()))
        );

        storageInterface.move(tenantId, null, new URI("/source.csv"), new URI("/dest.csv"));

        StorageObject moved = storageInterface.getWithMetadata(tenantId, null, new URI("/dest.csv"));
        assertThat(moved.metadata(), notNullValue());
        assertThat(moved.metadata(), hasEntry("someMetadata", "someValue"));
        assertFalse(storageInterface.exists(tenantId, null, new URI("/source.csv")));
    }

    @Test
    void shouldDeleteObjectWithCompanionMetadata() throws URISyntaxException, IOException {
        String tenantId = IdUtils.create();
        storageInterface.put(
            tenantId,
            null,
            new URI("/file.txt"),
            new StorageObject(Map.of("someMetadata", "someValue"), new ByteArrayInputStream("data".getBytes()))
        );

        assertTrue(storageInterface.delete(tenantId, null, new URI("/file.txt")));
        assertFalse(storageInterface.exists(tenantId, null, new URI("/file.txt")));

        // list() excludes metadata files, so verify the file directly.
        LocalStorage localStorage = assertInstanceOf(LocalStorage.class, storageInterface);
        Path orphanMetadataPath = localStorage.getBasePath().toAbsolutePath()
            .resolve(tenantId)
            .resolve("file.txt.metadata");
        assertFalse(Files.exists(orphanMetadataPath));
    }
}
