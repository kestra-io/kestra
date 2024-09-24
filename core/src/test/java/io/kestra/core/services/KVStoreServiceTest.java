package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.kv.KVStoreException;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@KestraTest
class KVStoreServiceTest {

    private static final String TEST_EXISTING_NAMESPACE = "io.kestra.unittest";

    @Inject
    KVStoreService storeService;

    @Inject
    FlowRepositoryInterface flowRepository;

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

    @Replaces(NamespaceService.class)
    @Singleton
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