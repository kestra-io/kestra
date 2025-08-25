package io.kestra.core.reporter.reports;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.PluginMetric;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.reporter.AbstractReportable;
import io.kestra.core.reporter.Schedules;
import io.kestra.core.reporter.Types;
import io.kestra.core.utils.ListUtils;
import io.micrometer.core.instrument.Timer;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class PluginMetricReport extends AbstractReportable<PluginMetricReport.PluginMetricEvent> {
    
    private final PluginRegistry pluginRegistry;
    private final MetricRegistry metricRegistry;
    private final boolean enabled;
    
    @Inject
    public PluginMetricReport(PluginRegistry pluginRegistry,
                              MetricRegistry metricRegistry) {
        super(Types.PLUGIN_METRICS, Schedules.daily(), false);
        this.metricRegistry = metricRegistry;
        this.pluginRegistry = pluginRegistry;
        
        ServerType serverType = KestraContext.getContext().getServerType();
        this.enabled = ServerType.SCHEDULER.equals(serverType) || ServerType.WORKER.equals(serverType) || ServerType.STANDALONE.equals(serverType);
    }
    
    @Override
    public PluginMetricEvent report(final Instant now, final TimeInterval period) {
        return PluginMetricEvent
            .builder()
            .pluginMetrics(pluginMetrics())
            .build();
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Builder
    @Introspected
    public record PluginMetricEvent (
        List<PluginMetric> pluginMetrics
    ) implements Event {
    }
    
    private List<PluginMetric> pluginMetrics() {
        List<PluginMetric> taskMetrics = pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getTasks().stream())
            .map(Class::getName)
            .map(this::taskMetric)
            .flatMap(Optional::stream)
            .toList();
        
        List<PluginMetric> triggerMetrics = pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getTriggers().stream())
            .map(Class::getName)
            .map(this::triggerMetric)
            .flatMap(Optional::stream)
            .toList();
        
        return ListUtils.concat(taskMetrics, triggerMetrics);
    }
    
    private Optional<PluginMetric> taskMetric(String type) {
        Timer duration = metricRegistry.find(MetricRegistry.METRIC_WORKER_ENDED_DURATION).tag(MetricRegistry.TAG_TASK_TYPE, type).timer();
        return fromTimer(type, duration);
    }
    
    private Optional<PluginMetric> triggerMetric(String type) {
        Timer duration = metricRegistry.find(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION).tag(MetricRegistry.TAG_TRIGGER_TYPE, type).timer();
        
        if (duration == null) {
            // this may be because this is a trigger executed by the scheduler, we search there instead
            duration = metricRegistry.find(MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION).tag(MetricRegistry.TAG_TRIGGER_TYPE, type).timer();
        }
        return fromTimer(type, duration);
    }
    
    private Optional<PluginMetric> fromTimer(String type, Timer timer) {
        if (timer == null || timer.count() == 0) {
            return Optional.empty();
        }
        
        double count = timer.count();
        double totalTime = timer.totalTime(TimeUnit.MILLISECONDS);
        double meanTime = timer.mean(TimeUnit.MILLISECONDS);
        
        return Optional.of(new PluginMetric(type, count, totalTime, meanTime));
    }
}
