package io.kestra.core.runners.test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerService;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

@SuperBuilder
@NoArgsConstructor
@Plugin(
    aliases = "io.kestra.core.runners.test.trigger.Alias"
)
public class TriggerWithAlias extends AbstractTrigger implements PollingTriggerInterface {

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws IllegalVariableEvaluationException {
        return Optional.empty();
    }

    @Override
    public Duration getInterval() {
        return Duration.of(1, ChronoUnit.HOURS);
    }
}
