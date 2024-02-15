package io.kestra.core.server;

import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class ServiceInstanceTest {

    public static final ServerConfig CONFIG = new ServerConfig(Duration.ZERO,
        new ServerConfig.Liveness(
            true,
            Duration.ZERO,
            Duration.ofSeconds(10), // timeout
            Duration.ZERO,
            Duration.ZERO
        )
    );

    @Test
    void shouldGetFalseForRunningAndNotTimeout() {
        // Given
        Instant now = Instant.now();
        ServiceInstance instance = new ServiceInstance(
            IdUtils.create(),
            Service.ServiceType.WORKER,
            Service.ServiceState.RUNNING,
            null,
            now.minus(Duration.ofSeconds(5)),
            now.minus(Duration.ofSeconds(5)),
            null,
            CONFIG,
            null
        );

        // When - Then
        Assertions.assertFalse(instance.isSessionTimeoutElapsed(now));
    }

    @Test
    void shouldGetTrueForRunningAndTimeout() {
        // Given
        Instant now = Instant.now();
        ServiceInstance instance = new ServiceInstance(
            IdUtils.create(),
            Service.ServiceType.WORKER,
            Service.ServiceState.RUNNING,
            null,
            now.minus(Duration.ofSeconds(20)),
            now.minus(Duration.ofSeconds(20)),
            null,
            CONFIG,
            null
        );

        // When - Then
        Assertions.assertTrue(instance.isSessionTimeoutElapsed(now));
    }

    @Test
    void shouldUpdateGivenReason() {
        // Given
        Instant now = Instant.now();
        ServiceInstance instance = new ServiceInstance(
            IdUtils.create(),
            Service.ServiceType.WORKER,
            Service.ServiceState.RUNNING,
            null,
            now,
            now,
            List.of(),
            CONFIG,
            null
        );

        // When
        ServiceInstance result = instance.updateState(Service.ServiceState.DISCONNECTED, now, "Disconnected");

        // Then
        Assertions.assertNotEquals(instance, result);
        Assertions.assertEquals(result.events(), List.of(new ServiceInstance.TimestampedEvent(now, "Disconnected")));
    }
}