package io.kestra.storage.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.utils.IdUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
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
}

