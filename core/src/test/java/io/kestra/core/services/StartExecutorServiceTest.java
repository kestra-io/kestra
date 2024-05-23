package io.kestra.core.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "kestra.queue.type", value = "kafka")
class StartExecutorServiceTest {
    @Inject
    private StartExecutorService startExecutorService;

    @Test
    void shouldStartExecutor() {
        assertTrue(startExecutorService.shouldStartExecutor("ExecutorName"));

        startExecutorService.applyOptions(List.of("ExecutorName"), Collections.emptyList());
        assertTrue(startExecutorService.shouldStartExecutor("ExecutorName"));

        startExecutorService.applyOptions(List.of("AnotherExecutorName"), Collections.emptyList());
        assertFalse(startExecutorService.shouldStartExecutor("ExecutorName"));

        startExecutorService.applyOptions(Collections.emptyList(), List.of("AnotherExecutorName"));
        assertTrue(startExecutorService.shouldStartExecutor("ExecutorName"));

        startExecutorService.applyOptions(Collections.emptyList(), List.of("ExecutorName"));
        assertFalse(startExecutorService.shouldStartExecutor("ExecutorName"));

        assertThrows(IllegalArgumentException.class, () -> startExecutorService.applyOptions(List.of("ExecutorName"), List.of("AnotherExecutorName")));
    }

}