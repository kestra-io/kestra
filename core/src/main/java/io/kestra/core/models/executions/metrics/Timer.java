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
import java.time.Duration;
import java.util.Map;

@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Timer extends AbstractMetricEntry<Duration> {
    public static final String TYPE = "timer";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    @EqualsAndHashCode.Exclude
    private Duration value;

    private Timer(@NotNull String name, @Nullable String description, @NotNull Duration value, String... tags) {
        super(name, description, tags);

        this.value = value;
    }

    public static Timer of(@NotNull String name, @NotNull Duration value, String... tags) {
        return new Timer(name, null, value, tags);
    }

    public static Timer of(@NotNull String name, @Nullable String description, @NotNull Duration value, String... tags) {
        return new Timer(name, description, value, tags);
    }

    @Override
    public void register(MetricRegistry meterRegistry, String name, String description, Map<String, String> tags) {
        meterRegistry
            .timer(this.metricName(name), description, this.tagsAsArray(tags))
            .record(this.value);
    }

    @Override
    public void increment(Duration value) {
        this.value = this.value.plus(value);
    }
}
