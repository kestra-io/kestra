package io.kestra.core.models.triggers;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface PollingTriggerInterface {
    Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception;

    default ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
        return ZonedDateTime.now();
    }

    @Schema(
        title = "Interval between polling.",
        description = "The interval between 2 different polls of schedule, this can avoid to overload the remote system " +
            "with too many calls. For most of the triggers that depend on external systems, a minimal interval must be " +
            "at least PT30S.\n" +
            "See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available interval values."
    )
    @PluginProperty
    Duration getInterval();
}
