package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableMap;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Counter.class, name = "counter"),
    @JsonSubTypes.Type(value = Timer.class, name = "timer"),
})
@Getter
@NoArgsConstructor
@Introspected
abstract public class AbstractMetricEntry <T> {
    @NotNull
    protected String name;

    protected Map<String, String> tags;

    protected AbstractMetricEntry(@NotNull String name, String[] tags) {
        this.name = name;
        this.tags = tagsAsMap(tags);
    }

    private static Map<String, String> tagsAsMap(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return ImmutableMap.of();
        }

        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }

        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        for (int i = 0; i < keyValues.length; i += 2) {
            builder.put(keyValues[i], keyValues[i + 1]);
        }

        return builder.build();
    }

    protected String[] tagsAsArray(Map<String, String> others) {
        return Stream.concat(
            this.tags.entrySet().stream(),
            others.entrySet().stream()
        )
            .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
            .collect(Collectors.toList())
            .toArray(String[]::new);
    }

    protected String metricName(String prefix) {
        return prefix == null ? this.name : prefix + "." + this.name;
    }

    abstract public T getValue();

    abstract public void register(MetricRegistry meterRegistry, String prefix, Map<String, String> tags);
}
