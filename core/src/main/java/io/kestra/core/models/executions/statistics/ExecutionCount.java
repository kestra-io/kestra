package io.kestra.core.models.executions.statistics;

import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
public class ExecutionCount {
    @NotNull
    protected String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private Long count;
}
