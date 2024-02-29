package io.kestra.core.models.conditions;

import lombok.*;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.runners.RunContext;

import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import jakarta.validation.constraints.NotNull;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConditionContext {
    @NotNull
    private Flow flow;

    private Execution execution;

    @With
    @NotNull
    private RunContext runContext;

    @With
    @Builder.Default
    private final Map<String, Object> variables = new HashMap<>();

    @Nullable
    private MultipleConditionStorageInterface multipleConditionStorage;
}
