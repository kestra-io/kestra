package io.kestra.opentelemetry;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

@MicronautTest
class OpenTelemetryEnabledTest {
    @Inject
    EmbeddedApplication<?> application;

    @Test
    void testServerStartsWithOpenTelemetry() {
        Assertions.assertTrue(application.isRunning());
    }
}