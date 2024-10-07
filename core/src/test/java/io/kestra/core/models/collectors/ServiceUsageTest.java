package io.kestra.core.models.collectors;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ServiceUsageTest {


    @Test
    void shouldGetDailyUsage() {
        // Given
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.getMonth().length(start.isLeapYear()));

        List<ServiceInstance> instances = new ArrayList<>();
        while (start.toEpochDay() < end.toEpochDay()) {
            Instant createAt = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant updatedAt = start.atStartOfDay(ZoneId.systemDefault()).plus(Duration.ofHours(10)).toInstant();
            ServiceInstance instance = new ServiceInstance(
                IdUtils.create(),
                Service.ServiceType.WORKER,
                Service.ServiceState.EMPTY,
                null,
                createAt,
                updatedAt,
                List.of(),
                null,
                Map.of(),
                Set.of()
            );
            instance = instance
                .state(Service.ServiceState.RUNNING, createAt)
                .state(Service.ServiceState.NOT_RUNNING, updatedAt);
            instances.add(instance);
            start = start.plusDays(1);
        }

        // When
        ServiceUsage.DailyServiceStatistics statistics = ServiceUsage.of(
            Service.ServiceType.WORKER,
            Duration.ofMinutes(15),
            instances
        );

        // Then
        Assertions.assertEquals(instances.size(), statistics.values().size());
    }
}