package io.kestra.core.reporter.reports;

import java.time.Instant;
import java.util.Objects;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.ExecutionUsage;
import io.kestra.core.models.collectors.FlowUsage;
import io.kestra.core.models.collectors.MetricUsage;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.kestra.core.reporter.model.Count;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Singleton
@Requires(property = "kestra.server-type", pattern = "STANDALONE|EXECUTOR|WEBSERVER")
public class FeatureUsageReport extends AbstractReportable<FeatureUsageReport.UsageEvent> {

    private final FlowRepositoryInterface flowRepository;
    private final ExecutionStatisticsRepositoryInterface executionStatisticRepository;
    private final DashboardRepositoryInterface dashboardRepository;
    private final ServerType serverType;
    private final MetricRegistry metricRegistry;

    @Inject
    public FeatureUsageReport(FlowRepositoryInterface flowRepository,
        ExecutionStatisticsRepositoryInterface executionStatisticRepository,
        DashboardRepositoryInterface dashboardRepository,
        @Value("${kestra.server-type}") ServerType serverType,
        MetricRegistry metricRegistry) {
        super(Types.USAGE, Schedules.hourly(), true);
        this.flowRepository = flowRepository;
        this.executionStatisticRepository = executionStatisticRepository;
        this.dashboardRepository = dashboardRepository;
        this.serverType = serverType;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public boolean isEnabled() {
        return serverType.equals(ServerType.EXECUTOR) || serverType.equals(ServerType.STANDALONE);
    }

    @Override
    public UsageEvent report(final Instant now, TimeInterval interval) {
        return UsageEvent
            .builder()
            .flows(FlowUsage.of(flowRepository))
            .executions(ExecutionUsage.of(executionStatisticRepository, interval.from(), interval.to()))
            .dashboards(new Count(dashboardRepository.countAllForAllTenants()))
            .metrics(MetricUsage.of(metricRegistry))
            .build();
    }

    @Override
    public UsageEvent report(Instant now, TimeInterval interval, String tenant) {
        Objects.requireNonNull(tenant, "tenant is null");
        Objects.requireNonNull(interval, "interval is null");
        return UsageEvent
            .builder()
            .flows(FlowUsage.of(tenant, flowRepository))
            .executions(ExecutionUsage.of(tenant, executionStatisticRepository, interval.from(), interval.to()))
            .metrics(MetricUsage.of(metricRegistry))
            .build();
    }

    @SuperBuilder(toBuilder = true)
    @Getter
    @Jacksonized
    public static class UsageEvent implements Event {
        private ExecutionUsage executions;
        private FlowUsage flows;
        private Count dashboards;
        private MetricUsage metrics;
    }
}
