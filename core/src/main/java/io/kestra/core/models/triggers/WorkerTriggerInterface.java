package io.kestra.core.models.triggers;

import java.time.ZonedDateTime;
import java.util.Optional;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.scheduler.SchedulerClock;

/**
 * Interface for triggers whose schedule is owned by the Scheduler: it holds a trigger state for them and
 * evaluates them at the date returned by {@link #nextEvaluationDate(ConditionContext, Optional)}.
 * <p>
 * Whether the evaluation then runs inside the Scheduler or is dispatched to a Worker is decided by the
 * sub-interface: {@link Schedulable} is evaluated in place, {@link PollingTriggerInterface} and
 * {@link RealtimeTriggerInterface} are sent to a Worker.
 */
public interface WorkerTriggerInterface {

    /**
     * Compute the next evaluation date of the trigger based on the existing trigger context.
     */
    default ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws InvalidTriggerConfigurationException {
        return nextEvaluationDate();
    }

    /**
     * Compute the next evaluation date of the trigger, used to initialize it when it has no evaluation date yet.
     */
    default ZonedDateTime nextEvaluationDate() throws InvalidTriggerConfigurationException {
        return SchedulerClock.now();
    }
}
