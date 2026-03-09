package io.kestra.core.reporter.reports;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.ExecutionUsage;
import io.kestra.core.models.collectors.FlowUsage;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.kestra.core.reporter.model.Count;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Objects;

@Singleton
public class FeatureUsageReport extends AbstractReportable<FeatureUsageReport.UsageEvent> {

    private final FlowRepositoryInterface flowRepository;
    private final ExecutionRepositoryInterface executionRepository;
    private final DashboardRepositoryInterface dashboardRepository;
    private final boolean enabled;

    @Inject
    public FeatureUsageReport(FlowRepositoryInterface flowRepository,
                              ExecutionRepositoryInterface executionRepository,
                              DashboardRepositoryInterface dashboardRepository) {
        super(Types.USAGE, Schedules.hourly(), true);
        this.flowRepository = flowRepository;
        this.executionRepository = executionRepository;
        this.dashboardRepository = dashboardRepository;

        ServerType serverType = KestraContext.getContext().getServerType();
        this.enabled = ServerType.EXECUTOR.equals(serverType) || ServerType.STANDALONE.equals(serverType);
    }

    @Override
    public UsageEvent report(final Instant now, TimeInterval interval) {
        return UsageEvent
            .builder()
            .flows(FlowUsage.of(flowRepository))
            .executions(ExecutionUsage.of(executionRepository, interval.from(), interval.to()))
            .dashboards(new Count(dashboardRepository.countAllForAllTenants()))
            .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public UsageEvent report(Instant now, TimeInterval interval, String tenant) {
        Objects.requireNonNull(tenant, "tenant is null");
        Objects.requireNonNull(interval, "interval is null");
        return UsageEvent
            .builder()
            .flows(FlowUsage.of(tenant, flowRepository))
            .executions(ExecutionUsage.of(tenant, executionRepository, interval.from(), interval.to()))
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @Jacksonized
    @Introspected
    public static class UsageEvent implements Event {
        private ExecutionUsage executions;
        private FlowUsage flows;
        private Count dashboards;
    }
}
