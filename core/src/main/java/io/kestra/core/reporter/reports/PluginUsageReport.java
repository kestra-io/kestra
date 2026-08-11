package io.kestra.core.reporter.reports;

import java.time.Instant;
import java.util.List;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.PluginUsage;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.Builder;

@Singleton
public class PluginUsageReport extends AbstractReportable<PluginUsageReport.PluginUsageEvent> {

    // Lazy: PluginRegistry may not be fully accessible when this class is instantiated
    private final Provider<PluginRegistry> pluginRegistry;
    private final boolean enabled;

    @Inject
    public PluginUsageReport(Provider<PluginRegistry> pluginRegistry) {
        super(Types.PLUGIN_USAGE, Schedules.daily(), false);
        this.pluginRegistry = pluginRegistry;

        ServerType serverType = KestraContext.getContext().getServerType();
        this.enabled = ServerType.EXECUTOR.equals(serverType) || ServerType.STANDALONE.equals(serverType);
    }

    @Override
    public PluginUsageEvent report(final Instant now, final TimeInterval period) {
        return PluginUsageEvent
            .builder()
            .plugins(PluginUsage.of(pluginRegistry.get()))
            .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Builder
    public record PluginUsageEvent(
        List<PluginUsage> plugins) implements Event {
    }
}
