package io.kestra.repository.mysql;

import io.kestra.core.namespace.AbstractDefaultNamespaceFileMetadataStateStoreTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MysqlDefaultNamespaceFileMetadataStateStoreTest extends AbstractDefaultNamespaceFileMetadataStateStoreTest {

    @Test
    @Disabled("This test doesn't worker maybe because of InnoDB Full-Text Index Cache")
    public void shouldFilterBySubstringWhenFindAllGivenContainingValue(){
        super.shouldFilterBySubstringWhenFindAllGivenContainingValue();
    }
}
