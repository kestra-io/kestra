package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@Builder
@EqualsAndHashCode
@ToString
public class ExecutionKilled {
    @NotNull
    private String executionId;
}
