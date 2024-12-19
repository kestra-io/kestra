package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@KestraTest
class KVStoreServiceTest {

    private static final String TEST_EXISTING_NAMESPACE = "io.kestra.unittest";

    @Inject
    KVStoreService storeService;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldGetKVStoreForExistingNamespaceGivenFromNull() {
        Assertions.assertNotNull(storeService.get(null, TEST_EXISTING_NAMESPACE, null));
    }

    @Test
    void shouldThrowExceptionWhenAccessingKVStoreForNonExistingNamespace() {
        KVStoreException exception = Assertions.assertThrows(KVStoreException.class, () -> storeService.get(null, "io.kestra.unittest.unknown", null));
        Assertions.assertTrue(exception.getMessage().contains("namespace 'io.kestra.unittest.unknown' does not exist"));
    }

    @Test
    void shouldGetKVStoreForAnyNamespaceWhenAccessingFromChildNamespace() {
        Assertions.assertNotNull(storeService.get(null, "io.kestra", TEST_EXISTING_NAMESPACE));
    }

    @Test
    void shouldGetKVStoreFromNonExistingNamespaceWithAKV() throws IOException {
        KVStore kvStore = new InternalKVStore(null, "system", storageInterface);
        kvStore.put("key", new KVValueAndMetadata(new KVMetadata(Duration.ofHours(1)), "value"));
        Assertions.assertNotNull(storeService.get(null, "system", null));
    }

    @MockBean(NamespaceService.class)
    public static class MockNamespaceService extends NamespaceService {

        public MockNamespaceService() {
            super(Optional.empty());
        }

        @Override
        public boolean isNamespaceExists(String tenant, String namespace) {
            return namespace.equals(TEST_EXISTING_NAMESPACE);
        }
    }
}