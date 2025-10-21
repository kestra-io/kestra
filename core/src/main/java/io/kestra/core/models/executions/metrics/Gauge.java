package io.kestra.core.models.executions.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
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
public class Gauge extends AbstractMetricEntry<Double> {
    public static final String TYPE = "gauge";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    @EqualsAndHashCode.Exclude
    private Double value;

    private Gauge(@NotNull String name, @Nullable String description, @NotNull Double value, String... tags) {
        super(name, description, tags);

        this.value = value;
    }

    public static Gauge of(@NotNull String name, @NotNull Double value, String... tags) {
        return new Gauge(name, null, value, tags);
    }

    public static Gauge of(@NotNull String name, @Nullable String description, @NotNull Double value, String... tags) {
        return new Gauge(name, description, value, tags);
    }

    public static Gauge of(@NotNull String name, @NotNull Integer value, String... tags) {
        return new Gauge(name, null, (double) value, tags);
    }

    public static Gauge of(@NotNull String name, @Nullable String description, @NotNull Integer value, String... tags) {
        return new Gauge(name, description, (double) value, tags);
    }

    public static Gauge of(@NotNull String name, @NotNull Long value, String... tags) {
        return new Gauge(name, null, (double) value, tags);
    }

    public static Gauge of(@NotNull String name, @Nullable String description, @NotNull Long value, String... tags) {
        return new Gauge(name, description, (double) value, tags);
    }

    public static Gauge of(@NotNull String name, @NotNull Float value, String... tags) {
        return new Gauge(name, null, (double) value, tags);
    }

    public static Gauge of(@NotNull String name, @Nullable String description, @NotNull Float value, String... tags) {
        return new Gauge(name, description, (double) value, tags);
    }

    @Override
    public void register(MetricRegistry meterRegistry, String name, String description, Map<String, String> tags) {
        meterRegistry
                .gauge(this.metricName(name), description, this.value, this.tagsAsArray(tags));
    }

    @Override
    public void increment(Double value) {
        this.value = value;
    }
}
