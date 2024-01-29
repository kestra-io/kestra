package io.kestra.core.models.executions;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import jakarta.validation.constraints.NotNull;

@Value
@Builder
@EqualsAndHashCode
@ToString
public class ExecutionKilled {
    @NotNull
    String executionId;
}
