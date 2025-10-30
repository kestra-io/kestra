package io.kestra.core.reporter.reports;

import io.kestra.core.models.collectors.ConfigurationUsage;
import io.kestra.core.models.collectors.HostUsage;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Set;

@Singleton
public class SystemInformationReport extends AbstractReportable<SystemInformationReport.SystemInformationEvent> {
    
    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final String kestraUrl;
    private final Instant startTime;
    
    @Inject
    public SystemInformationReport(ApplicationContext applicationContext) {
        super(Types.SYSTEM_INFORMATION, Schedules.daily(), false);
        this.environment = applicationContext.getEnvironment();
        this.applicationContext = applicationContext;
        this.kestraUrl = applicationContext.getProperty("kestra.url", String.class).orElse(null);
        this.startTime = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    }
    
    @Override
    public SystemInformationEvent report(final Instant now, final TimeInterval timeInterval) {
        return SystemInformationEvent
            .builder()
            .environments(environment.getActiveNames())
            .configurations(ConfigurationUsage.of(applicationContext))
            .startTime(startTime)
            .host(HostUsage.of())
            .uri(kestraUrl)
            .build();
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Builder
    @Introspected
    public record SystemInformationEvent(
        Set<String> environments,
        HostUsage host,
        ConfigurationUsage configurations,
        Instant startTime,
        String uri
    ) implements Event {
    }
}
