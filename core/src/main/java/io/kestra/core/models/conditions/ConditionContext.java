package io.kestra.core.models.conditions;

import io.kestra.core.models.flows.FlowInterface;
import lombok.*;
import io.kestra.core.models.executions.Execution;
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
    private FlowInterface flow;

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
