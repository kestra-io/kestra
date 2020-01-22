package org.kestra.core.models.executions.metrics;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.AbstractMetricEntry;

import javax.validation.constraints.NotNull;
import java.util.Map;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public final class Counter extends AbstractMetricEntry<Double> {
    @NotNull
    private Double value;

    @NotNull
    protected String type = "counter";

    private Counter(@NotNull String name, @NotNull Double value, String... tags) {
        super(name, tags);

        this.value = value;
    }

    public static Counter of(@NotNull String name, @NotNull Double value, String... tags) {
        return new Counter(name, value, tags);
    }

    public static Counter of(@NotNull String name, @NotNull Integer value, String... tags) {
        return new Counter(name, (double) value, tags);
    }

    public static Counter of(@NotNull String name, @NotNull Long value, String... tags) {
        return new Counter(name, (double) value, tags);
    }

    public static Counter of(@NotNull String name, @NotNull Float value, String... tags) {
        return new Counter(name, (double) value, tags);
    }

    @Override
    public void register(MetricRegistry meterRegistry, String prefix, Map<String, String> tags) {
        meterRegistry
            .counter(this.metricName(prefix), this.tagsAsArray(tags))
            .increment(this.value);
    }
}
