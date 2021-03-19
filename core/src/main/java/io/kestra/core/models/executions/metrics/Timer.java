package io.kestra.core.models.executions.metrics;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;

import javax.validation.constraints.NotNull;
import java.util.Map;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Timer extends AbstractMetricEntry<java.time.Duration> {
    @NotNull
    protected String type = "timer";

    @NotNull
    private java.time.Duration value;

    private Timer(@NotNull String name, @NotNull java.time.Duration value, String... tags) {
        super(name, tags);

        this.value = value;
    }

    public static Timer of(@NotNull String name, @NotNull java.time.Duration value, String... tags) {
        return new Timer(name, value, tags);
    }

    @Override
    public void register(MetricRegistry meterRegistry, String prefix, Map<String, String> tags) {
        meterRegistry
            .timer(this.metricName(prefix), this.tagsAsArray(tags))
            .record(this.value);
    }
}
