package io.kestra.core.models.conditions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.runners.RunContext;

import io.micronaut.core.annotation.Nullable;
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

    @Nullable
    private MultipleConditionStorageInterface multipleConditionStorage;
}
