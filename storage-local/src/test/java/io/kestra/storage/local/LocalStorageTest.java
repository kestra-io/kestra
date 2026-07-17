package io.kestra.storage.local;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageTest extends StorageTestSuite {
    // Launch test from StorageTestSuite

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
}
