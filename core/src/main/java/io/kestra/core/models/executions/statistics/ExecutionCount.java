package io.kestra.core.models.executions.statistics;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class ExecutionCount {
    @NotNull
    String namespace;

    String flowId;

    @NotNull
    Long count;
}
