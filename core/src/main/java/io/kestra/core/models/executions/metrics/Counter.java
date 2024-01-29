package io.kestra.core.models.executions.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.AbstractMetricEntry;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public final class Counter extends AbstractMetricEntry<Double> {
    public static final String TYPE = "counter";
    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    @EqualsAndHashCode.Exclude
    private Double value;

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

    @Override
    public void increment(Double value) {
        this.value = this.value + value;
    }
}
