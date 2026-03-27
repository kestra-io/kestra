package io.kestra.core.models.conditions;

import java.util.HashMap;
import java.util.Map;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.runners.RunContext;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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
