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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void deleteByPrefixWithSpaceInFilename() throws Exception {
        String tenantId = IdUtils.create();
        String namespace = IdUtils.create();
        String filePath = "/" + namespace + "/storage/File Test Template.md";
        URI fileUri = new URI(null, null, filePath, null);

        storageInterface.put(
            tenantId,
            namespace,
            fileUri,
            new ByteArrayInputStream("Hello World".getBytes())
        );

        var deleted = storageInterface.deleteByPrefix(
            tenantId,
            namespace,
            new URI(null, null, "/" + namespace + "/storage/", null)
        );

        URI expected = new URI("kestra", "", filePath, null, null);
        assertTrue(deleted.contains(expected));
        assertTrue(deleted.stream().anyMatch(uri -> filePath.equals(uri.getPath())));
        assertFalse(storageInterface.exists(tenantId, namespace, fileUri));
    }
}
