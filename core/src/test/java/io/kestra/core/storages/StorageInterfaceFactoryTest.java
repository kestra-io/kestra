package io.kestra.core.storages;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.storage.local.LocalStorage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@MicronautTest
class StorageInterfaceFactoryTest {

    DefaultPluginRegistry registry = DefaultPluginRegistry.getOrCreate();

    @Inject
    Validator validator;

    @Test
    void shouldReturnStorageGivenValidId() {
        StorageInterface storage = StorageInterfaceFactory.make(registry, "local", Map.of("basePath", "/tmp/kestra"), validator);
        Assertions.assertNotNull(storage);
        Assertions.assertEquals(LocalStorage.class.getName(), storage.getType());
    }

    @Test
    void shouldFailedGivenInvalidId() {
        Assertions.assertThrows(KestraRuntimeException.class,
            () -> StorageInterfaceFactory.make(registry, "invalid", Map.of(), validator));
    }

    @Test
    void shouldFailedGivenInvalidConfig() {
        KestraRuntimeException e = Assertions.assertThrows(KestraRuntimeException.class,
            () -> StorageInterfaceFactory.make(registry, "local", Map.of(), validator));

        Assertions.assertTrue(e.getCause() instanceof ConstraintViolationException);
        Assertions.assertEquals("basePath: must not be null", e.getCause().getMessage());
    }
}