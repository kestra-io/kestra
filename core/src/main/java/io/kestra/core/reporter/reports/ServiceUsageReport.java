package io.kestra.core.reporter.reports;

import java.time.Duration;
import java.time.Instant;

import io.kestra.core.models.collectors.ServiceUsage;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;

@Singleton
@Requires(property = "kestra.server-type", pattern = "STANDALONE|EXECUTOR|WEBSERVER")
public class ServiceUsageReport extends AbstractReportable<ServiceUsageReport.ServiceUsageEvent> {

    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    @Inject
    public ServiceUsageReport(ServiceInstanceRepositoryInterface serviceInstanceRepository) {
        super(Types.SERVICE_USAGE, Schedules.daily(), false);
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    @Override
    public ServiceUsageEvent report(final Instant now, final TimeInterval period) {

        return ServiceUsageEvent
            .builder()
            .services(ServiceUsage.of(period.from().toInstant(), period.to().toInstant(), serviceInstanceRepository, Duration.ofMinutes(5)))
            .build();
    }

    @Builder
    public record ServiceUsageEvent(
        ServiceUsage services) implements Event {
    }
}
