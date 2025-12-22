package io.kestra.core.reporter.reports;

import io.kestra.core.models.collectors.ServiceUsage;
import io.kestra.core.reporter.Reportable;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@MicronautTest
public abstract class AbstractServiceUsageReportTest {

    @Inject
    ServiceUsageReport serviceUsageReport;

    @Inject
    ServiceInstanceRepositoryInterface serviceInstanceRepository;

    @Test
    public void shouldGetReport() {
        // Given
        final LocalDate start = LocalDate.of(2025, 1, 1);
        final LocalDate end = start.withDayOfMonth(start.getMonth().length(start.isLeapYear()));
        final ZoneId zoneId = ZoneId.systemDefault();

        LocalDate from = start;
        int days = 0;
        // generate one month of service instance

        while (from.toEpochDay() < end.toEpochDay()) {
            Instant createAt = from.atStartOfDay(zoneId).toInstant();
            Instant updatedAt = from.atStartOfDay(zoneId).plus(Duration.ofHours(10)).toInstant();
            ServiceInstance instance = new ServiceInstance(
                IdUtils.create(),
                ServiceType.EXECUTOR,
                Service.ServiceState.INACTIVE,
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
            serviceInstanceRepository.save(instance);
            from = from.plusDays(1);
            days++;
        }


        // When
        Instant now = end.plusDays(1).atStartOfDay(zoneId).toInstant();
        ServiceUsageReport.ServiceUsageEvent event = serviceUsageReport.report(now,
            Reportable.TimeInterval.of(start.atStartOfDay(zoneId), end.plusDays(1).atStartOfDay(zoneId))
        );

        // Then
        List<ServiceUsage.DailyServiceStatistics> statistics = event.services().dailyStatistics();
        Assertions.assertEquals(ServiceType.values().length - 1, statistics.size());
        Assertions.assertEquals(
            days,
            statistics.stream().filter(it -> it.type().equalsIgnoreCase("EXECUTOR")).findFirst().get().values().size()
        );
    }
}