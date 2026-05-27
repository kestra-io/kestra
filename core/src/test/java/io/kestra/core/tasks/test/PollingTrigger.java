package io.kestra.core.tasks.test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public class PollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
    @PluginProperty
    @NotNull
    @Builder.Default
    private Long duration = 1000L;

    @Override
    public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
        // Try catch to avoid flaky test
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var evaluationResult = TriggerService.generateEvaluationResult(this, conditionContext, Collections.emptyMap());

        return Optional.of(evaluationResult);
    }

    @Override
    public Duration getInterval() {
        return Duration.of(1, ChronoUnit.MINUTES);
    }
}
