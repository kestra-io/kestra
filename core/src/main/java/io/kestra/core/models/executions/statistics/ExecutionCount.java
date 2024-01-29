package io.kestra.core.models.executions.statistics;

import lombok.Value;

import jakarta.validation.constraints.NotNull;

@Value
public class ExecutionCount {
    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    Long count;
}
