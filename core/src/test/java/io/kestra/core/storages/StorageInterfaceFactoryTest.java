package io.kestra.core.storages;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.storage.local.LocalStorage;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class StorageInterfaceFactoryTest {

    @Inject
    StorageInterfaceFactory storageInterfaceFactory;

    @Test
    void shouldReturnStorageGivenValidId() {
        StorageInterface storage = storageInterfaceFactory.make(null, "local", Map.of("basePath", "/tmp/kestra"));
        Assertions.assertNotNull(storage);
        assertEquals(LocalStorage.class.getName(), storage.getType());
    }

    @Test
    void shouldFailedGivenInvalidId() {
        assertThrows(
            KestraRuntimeException.class,
            () -> storageInterfaceFactory.make(null, "invalid", Map.of())
        );
    }

    @Test
    void shouldFailedGivenInvalidConfig() {
        KestraRuntimeException e = assertThrows(
            KestraRuntimeException.class,
            () -> storageInterfaceFactory.make(null, "local", Map.of())
        );

        assertTrue(e.getCause() instanceof ConstraintViolationException);
        assertEquals("basePath: must not be null", e.getCause().getMessage());
    }

    @Test
    void should_not_found_unknown_storage() {
        KestraRuntimeException e = assertThrows(
            KestraRuntimeException.class,
            () -> storageInterfaceFactory.make(null, "unknown", Map.of())
        );
        assertEquals("No storage interface can be found for 'kestra.storage.type=unknown'. Supported types are: [local]", e.getMessage());
    }
}