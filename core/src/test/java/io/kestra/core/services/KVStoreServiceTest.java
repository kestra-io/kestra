package io.kestra.core.services;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class KVStoreServiceTest {

    private static final String TEST_EXISTING_NAMESPACE = "io.kestra.unittest";

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
    void shouldDenyKVStoreAccessWhenAccessingFromPrefixSiblingNamespace() {
        assertThatThrownBy(() -> storeService.get(MAIN_TENANT, "prod", "prod2"))
            .isInstanceOf(KVStoreException.class)
            .hasMessageContaining("Access to 'prod' namespace is not allowed from 'prod2'");
    }

    @Test
    void shouldGetKVStoreFromNonExistingNamespaceWithAKV() throws IOException {
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, "system", storageInterface);
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

    @MockBean(FlowService.class)
    public static class MockFlowService extends FlowService {

        /** Emulates an EE allow-list where every namespace allows only itself, which OSS never denies. */
        @Override
        public boolean isAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
            return namespace.equals(fromNamespace);
        }
    }
}
