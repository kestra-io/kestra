package io.kestra.core.models.tasks.metrics;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Representation of a metric inside a Flow
 * Allow to build metrics with dynamic properties
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CounterMetric.class, name = "counter"),
    @JsonSubTypes.Type(value = TimerMetric.class, name = "timer"),
})
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class AbstractMetric {
    abstract public String getType();

    @NotNull
    protected Property<String> name;

    protected Property<Map<String, String>> tags;

    @NotNull
    @JsonInclude
    private String type;

    public abstract AbstractMetricEntry<?> toMetric(RunContext runContext) throws IllegalVariableEvaluationException;

}
