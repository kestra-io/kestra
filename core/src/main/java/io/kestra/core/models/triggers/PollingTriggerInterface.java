package io.kestra.core.models.triggers;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

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
     * Evaluate the trigger and create an execution if needed.
     */
    Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception;

    /**
     * Compute the next evaluation date of the trigger based on the existing trigger context: by default, it uses the current date and the interval.
     * Schedulable triggers must override this method.
     */
    default ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        return computeNextEvaluationDate(runContext);
    }

    /**
     * Compute the next evaluation date of the trigger: by default, it uses the current date and the interval.
     * Schedulable triggers must override this method as it's used to init them when there is no evaluation date.
     */
    default ZonedDateTime nextEvaluationDate() {
        return computeNextEvaluationDate(null);
    }

    /**
     * computes the next evaluation date using the configured interval.
     * Falls back to 60 seconds if the interval causes date overflow.
     */
    private ZonedDateTime computeNextEvaluationDate(@Nullable RunContext runContext) {
        Duration interval = this.getInterval();
        Logger logger = resolveLogger(runContext);

        try {
            return ZonedDateTime.now().plus(interval);
        } catch (DateTimeException | ArithmeticException e) {
            logger.warn("Trigger interval '{}' for type '{}' causes date overflow: {}. Using 60 seconds fallback.", interval, this.getClass().getName(), e.getMessage());
            return ZonedDateTime.now().plus(Duration.ofSeconds(60));
        }
    }

    private Logger resolveLogger(@Nullable RunContext runContext) {
        if (runContext != null) {
            return runContext.logger();
        }
        return LoggerFactory.getLogger(this.getClass());
    }
}
