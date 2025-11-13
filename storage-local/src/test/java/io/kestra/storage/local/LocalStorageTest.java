package io.kestra.storage.local;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.utils.IdUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
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
}
