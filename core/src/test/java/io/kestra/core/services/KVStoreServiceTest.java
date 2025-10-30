package io.kestra.core.services;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
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
    KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    KVStoreService storeService;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldGetKVStoreForExistingNamespaceGivenFromNull() {
        Assertions.assertNotNull(storeService.get(MAIN_TENANT, TEST_EXISTING_NAMESPACE, null));
    }

    @Test
    void shouldThrowExceptionWhenAccessingKVStoreForNonExistingNamespace() {
        KVStoreException exception = Assertions.assertThrows(KVStoreException.class, () -> storeService.get(MAIN_TENANT, "io.kestra.unittest.unknown", null));
        Assertions.assertTrue(exception.getMessage().contains("namespace 'io.kestra.unittest.unknown' does not exist"));
    }

    @Test
    void shouldGetKVStoreForAnyNamespaceWhenAccessingFromChildNamespace() {
        Assertions.assertNotNull(storeService.get(MAIN_TENANT, "io.kestra", TEST_EXISTING_NAMESPACE));
    }

    @Test
    void shouldGetKVStoreFromNonExistingNamespaceWithAKV() throws IOException {
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, "system", storageInterface, kvMetadataRepository);
        kvStore.put("key", new KVValueAndMetadata(new KVMetadata("myDescription", Duration.ofHours(1)), "value"));
        Assertions.assertNotNull(storeService.get(MAIN_TENANT, "system", null));
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
