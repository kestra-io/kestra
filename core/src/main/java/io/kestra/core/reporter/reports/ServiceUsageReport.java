package io.kestra.core.reporter.reports;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.ServiceUsage;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;

import java.time.Duration;
import java.time.Instant;

@Singleton
public class ServiceUsageReport extends AbstractReportable<ServiceUsageReport.ServiceUsageEvent> {
    
    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;
    private final boolean isEnabled;
    
    @Inject
    public ServiceUsageReport(ServiceInstanceRepositoryInterface serviceInstanceRepository) {
        super(Types.SERVICE_USAGE, Schedules.daily(), false);
        this.serviceInstanceRepository = serviceInstanceRepository;
        
        ServerType serverType = KestraContext.getContext().getServerType();
        this.isEnabled = ServerType.STANDALONE.equals(serverType) || ServerType.EXECUTOR.equals(serverType);
    }
    
    @Override
    public ServiceUsageEvent report(final Instant now, final TimeInterval period) {
        
        return ServiceUsageEvent
            .builder()
            .services(ServiceUsage.of(period.from().toInstant(), period.to().toInstant(), serviceInstanceRepository, Duration.ofMinutes(5)))
            .build();
    }
    
    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
    
    @Builder
    @Introspected
    public record ServiceUsageEvent(
        ServiceUsage services
    ) implements Event {
    }
}
