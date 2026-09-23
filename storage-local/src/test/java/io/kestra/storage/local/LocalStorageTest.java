package io.kestra.storage.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.utils.IdUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        String returned = StorageContext.logicalPath(put);
        assertThat(returned, not(longObjectName));
        String suffix = returned.substring(7); // leading slash, 5 random chars, and '-'
        assertTrue(longObjectName.endsWith(suffix));
    }

    @Test
    void shouldStoreCanonicalUriAtTheSameDiskPathAsLegacyUri() throws IOException {
        String tenantId = IdUtils.create();
        URI canonical = URI.create("kestra://namespace/folder/sub/script.py");
        storageInterface.put(tenantId, "namespace", canonical, new ByteArrayInputStream("same".getBytes()));

        Path stored = Path.of("/tmp/unittest", tenantId, "namespace/folder/sub/script.py");
        Path droppedAuthority = Path.of("/tmp/unittest", tenantId, "folder/sub/script.py");
        assertTrue(Files.exists(stored));
        assertFalse(Files.exists(droppedAuthority));
        assertTrue(storageInterface.exists(tenantId, "namespace", URI.create("kestra:///namespace/folder/sub/script.py")));
    }

    @Test
    void shouldRejectTraversalHiddenInTheAuthority() {
        assertThrows(
            IllegalArgumentException.class,
            () -> storageInterface.get(IdUtils.create(), null, URI.create("kestra://../etc/passwd"))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> storageInterface.get(IdUtils.create(), null, URI.create("kestra://..%2F..%2Fetc/passwd"))
        );
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
}
