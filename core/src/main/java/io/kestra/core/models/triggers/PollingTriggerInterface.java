package io.kestra.core.models.triggers;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.scheduler.SchedulerClock;
import io.swagger.v3.oas.annotations.media.Schema;

public interface PollingTriggerInterface extends WorkerTriggerInterface {
    @Schema(
        title = "Interval between polling.",
        description = "The interval between 2 different polls of schedule, this can avoid to overload the remote system " +
            "with too many calls. For most of the triggers that depend on external systems, a minimal interval must be " +
            "at least PT30S.\n" +
            "See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available interval values."
    )
    @PluginProperty
    Duration getInterval();

    /**
     * Evaluate the trigger and produce a lightweight result.
     * <p>
     * New plugins should override this method. The default bridges to the
     * deprecated {@link #evaluate} for backward compatibility.
     */
    default Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) throws Exception {
        return evaluate(conditionContext, context).map(TriggerEvaluationResult::from);
    }

    /**
     * Evaluate the trigger and create an execution if needed.
     *
     * @deprecated Override {@link #eval} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    default Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        throw new UnsupportedOperationException("Implement eval() instead of evaluate()");
    }

    /**
     * Compute the next evaluation date of the trigger based on the existing trigger context: by default, it uses the current date and the interval.
     * Schedulable triggers must override this method.
     */
    default ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws InvalidTriggerConfigurationException {
        return computeNextEvaluationDate();
    }

    /**
     * Compute the next evaluation date of the trigger: by default, it uses the current date and the interval.
     * Schedulable triggers must override this method as it's used to init them when there is no evaluation date.
     */
    default ZonedDateTime nextEvaluationDate() throws InvalidTriggerConfigurationException {
        return computeNextEvaluationDate();
    }

    /**
     * computes the next evaluation date using the configured interval.
     * Throw InvalidTriggerConfigurationException, if the interval causes date overflow.
     */
    private ZonedDateTime computeNextEvaluationDate() throws InvalidTriggerConfigurationException {
        Duration interval = this.getInterval();

        try {
            return SchedulerClock.now().plus(interval);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidTriggerConfigurationException("Trigger interval duration too large '" + interval + "'", e);
        }
    }
}
