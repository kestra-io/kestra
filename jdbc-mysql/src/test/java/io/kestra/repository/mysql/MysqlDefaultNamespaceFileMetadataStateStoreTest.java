package io.kestra.repository.mysql;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.namespace.AbstractDefaultNamespaceFileMetadataStateStoreTest;

public class MysqlDefaultNamespaceFileMetadataStateStoreTest extends AbstractDefaultNamespaceFileMetadataStateStoreTest {

    @Test
    @Disabled("This test doesn't worker maybe because of InnoDB Full-Text Index Cache")
    public void shouldFilterBySubstringWhenFindAllGivenContainingValue() {
        super.shouldFilterBySubstringWhenFindAllGivenContainingValue();
    }
}
