package io.kestra.core.models.executions.statistics;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class Flow {
    @NotNull
    String namespace;

    @NotNull
    String flowId;
}
