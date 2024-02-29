package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

@SuperBuilder
@NoArgsConstructor
public class PollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
    @PluginProperty
    @NotNull
    @Builder.Default
    private Long duration = 1000L;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
        // Try catch to avoid flaky test
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(
            this,
            Collections.emptyMap()
        );
        Execution execution = Execution.builder()
            .id(runContext.getTriggerExecutionId())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            .build();
        return Optional.of(execution);
    }

    @Override
    public Duration getInterval() {
        return Duration.of(1, ChronoUnit.MINUTES);
    }
}
