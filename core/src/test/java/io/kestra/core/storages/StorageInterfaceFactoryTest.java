package io.kestra.core.storages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.storage.local.LocalStorage;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@KestraTest
class StorageInterfaceFactoryTest {

    DefaultPluginRegistry registry = DefaultPluginRegistry.getOrCreate();

    @Inject
    Validator validator;

    @Test
    void shouldReturnStorageGivenValidId() {
        StorageInterface storage = StorageInterfaceFactory.make(registry, "local", Map.of("basePath", "/tmp/kestra"), validator);
        Assertions.assertNotNull(storage);
        assertEquals(LocalStorage.class.getName(), storage.getType());
    }

    @Test
    void shouldFailedGivenInvalidId() {
        assertThrows(KestraRuntimeException.class,
            () -> StorageInterfaceFactory.make(registry, "invalid", Map.of(), validator));
    }

    @Test
    void shouldFailedGivenInvalidConfig() {
        KestraRuntimeException e = assertThrows(KestraRuntimeException.class,
            () -> StorageInterfaceFactory.make(registry, "local", Map.of(), validator));

        assertTrue(e.getCause() instanceof ConstraintViolationException);
        assertEquals("basePath: must not be null", e.getCause().getMessage());
    }

    @Test
    void should_not_found_unknown_storage(){
        KestraRuntimeException e = assertThrows(KestraRuntimeException.class,
            () -> StorageInterfaceFactory.make(registry, "unknown", Map.of(), validator));
        assertEquals("No storage interface can be found for 'kestra.storage.type=unknown'. Supported types are: [local]", e.getMessage());
    }
}