package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;

/**
 * This trigger is used in unit tests where we need a task that wait a little to be able to check the resubmit of triggers.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class SleepTrigger extends AbstractTrigger implements PollingTriggerInterface {

    @PluginProperty
    @NotNull
    private Long duration;

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
        return null;
    }
}
