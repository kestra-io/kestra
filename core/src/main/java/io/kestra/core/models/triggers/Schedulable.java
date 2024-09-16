package io.kestra.core.models.triggers;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.runners.RunContext;

import java.time.ZonedDateTime;

public interface Schedulable extends PollingTriggerInterface{
    String PLUGIN_PROPERTY_RECOVER_MISSED_SCHEDULES = "recoverMissedSchedules";

    /**
     * Compute the previous evaluation of a trigger.
     * This is used when a trigger misses some schedule to compute the next date to evaluate in the past.
     */
    ZonedDateTime previousEvaluationDate(ConditionContext conditionContext) throws IllegalVariableEvaluationException;

    RecoverMissedSchedules getRecoverMissedSchedules();

    /**
     * Load the default RecoverMissedSchedules from plugin property, or else ALL.
     */
    default RecoverMissedSchedules defaultRecoverMissedSchedules(RunContext runContext) {
        return runContext
            .<String>pluginConfiguration(PLUGIN_PROPERTY_RECOVER_MISSED_SCHEDULES)
            .map(conf -> RecoverMissedSchedules.valueOf(conf))
            .orElse(RecoverMissedSchedules.ALL);
    }
}
