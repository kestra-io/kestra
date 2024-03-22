package io.kestra.core.models.triggers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface PollingTriggerInterface extends WorkerTriggerInterface, WorkerTriggerIntervalInterface {
    Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception;

    default ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
        return ZonedDateTime.now().plus(this.getInterval());
    }

    default ZonedDateTime nextEvaluationDate() {
        return ZonedDateTime.now().plus(this.getInterval());
    }
}
