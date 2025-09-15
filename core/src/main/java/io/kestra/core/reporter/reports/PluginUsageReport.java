package io.kestra.core.reporter.reports;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.PluginUsage;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Singleton
public class PluginUsageReport extends AbstractReportable<PluginUsageReport.PluginUsageEvent> {
    
    private final PluginRegistry pluginRegistry;
    private final boolean enabled;
    @Inject
    public PluginUsageReport(PluginRegistry pluginRegistry) {
        super(Types.PLUGIN_USAGE, Schedules.daily(), false);
        this.pluginRegistry = pluginRegistry;
        
        ServerType serverType = KestraContext.getContext().getServerType();
        this.enabled = ServerType.EXECUTOR.equals(serverType) || ServerType.STANDALONE.equals(serverType);
    }
    
    @Override
    public PluginUsageEvent report(final Instant now, final TimeInterval period) {
        return PluginUsageEvent
            .builder()
            .plugins(PluginUsage.of(pluginRegistry))
            .build();
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Builder
    @Introspected
    public record PluginUsageEvent(
        List<PluginUsage> plugins
    ) implements Event {
    }
}
