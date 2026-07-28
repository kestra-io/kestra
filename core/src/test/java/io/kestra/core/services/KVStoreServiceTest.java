package io.kestra.core.services;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.utils.IdUtils;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class KVStoreServiceTest {
    private static final String TEST_EXISTING_NAMESPACE = "io.kestra.unittest";

    @Inject
    KVMetadataStateStore kvMetadataStateStore;

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
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, "system", storageInterface, kvMetadataStateStore);
        kvStore.put("key", new KVValueAndMetadata(new KVMetadata("myDescription", Duration.ofHours(1)), "value"));
        Assertions.assertNotNull(storeService.get(MAIN_TENANT, "system", null));
    }

    @Test
    void shouldPurgeKVStoreForSpecificVersions() throws IOException {
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, IdUtils.create(), storageInterface, kvMetadataStateStore);
        String key = IdUtils.create();

        kvStore.put(key, new KVValueAndMetadata(null, "value1"));
        kvStore.put(key, new KVValueAndMetadata(null, "value2"));
        kvStore.put(key, new KVValueAndMetadata(null, "value3"));

        storeService.purge(
            MAIN_TENANT, kvStore.namespace(), List.of(
                new KVEntry(kvStore.namespace(), key, 1, null, Instant.now(), Instant.now(), null),
                new KVEntry(kvStore.namespace(), key, 3, null, Instant.now(), Instant.now(), null)
            )
        );

        List<KVEntry> kvEntries = storeService.listAll(MAIN_TENANT, kvStore.namespace());
        assertThat(kvEntries).hasSize(1);
        assertThat(kvEntries.getFirst().revision()).isEqualTo(2);
    }

    @MockBean(NamespaceService.class)
    public static class MockNamespaceService extends DefaultNamespaceService {

        public MockNamespaceService() {
            super(null);
        }

        @Override
        public boolean isNamespaceExists(String tenant, String namespace) {
            return namespace.equals(TEST_EXISTING_NAMESPACE);
        }
    }
}
