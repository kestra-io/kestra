package org.kestra.core.models.conditions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.runners.RunContext;

import javax.validation.constraints.NotNull;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConditionContext {
    @NotNull
    private Flow flow;

    private Execution execution;

    @NotNull
    private RunContext runContext;
}
