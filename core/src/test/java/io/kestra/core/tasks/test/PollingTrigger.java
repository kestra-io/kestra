package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@SuperBuilder
@NoArgsConstructor
public class PollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
    @PluginProperty
    @NotNull
    @Builder.Default
    private Long duration = 2000L;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
        // Try catch to avoid flakky test
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return Optional.empty();
    }

    @Override
    public Duration getInterval() {
        return Duration.of(1, ChronoUnit.MINUTES);
    }
}
