package io.kestra.core.server;

import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ServiceInstanceTest {

    public static final ServerConfig CONFIG = new ServerConfig(
        Duration.ZERO,
        WorkerTaskRestartStrategy.AFTER_TERMINATION_GRACE_PERIOD,
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
            null,
            Set.of()
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
            null,
            Set.of()
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
            null,
            Set.of()
        );

        // When
        ServiceInstance result = instance.state(Service.ServiceState.DISCONNECTED, now, "Disconnected");

        // Then
        Assertions.assertNotEquals(instance, result);
        Assertions.assertEquals(List.of(
            new ServiceInstance.TimestampedEvent(now, "Disconnected", "service.state.updated", Service.ServiceState.DISCONNECTED)), result.events());
    }

    @Test
    void shouldGroupInstanceGivenAnExistingProperty() {
        List<ServiceInstance> instances = List.of(
            createServiceInstanceWithProperties(Map.of("prop", "A")),
            createServiceInstanceWithProperties(Map.of("prop", "A")),
            createServiceInstanceWithProperties(Map.of("prop", "B")),
            createServiceInstanceWithProperties(Map.of())
        );

        Map<String, List<ServiceInstance>> grouped = ServiceInstance.groupByProperty(instances, "prop");
        Assertions.assertEquals(grouped.size(), 2);
        Assertions.assertEquals(grouped.get("A").size(), 2);
        Assertions.assertEquals(grouped.get("B").size(), 1);
    }

    private static ServiceInstance createServiceInstanceWithProperties(Map<String, Object> properties) {
        Instant now = Instant.now();
        return new ServiceInstance(
            IdUtils.create(),
            Service.ServiceType.WORKER,
            Service.ServiceState.RUNNING,
            null,
            now,
            now,
            List.of(),
            CONFIG,
            properties,
            Set.of()
        );
    }
}