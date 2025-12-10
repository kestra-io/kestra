package io.kestra.core.models.triggers;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.validations.TimezoneId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.TimeZone;

public interface Schedulable extends PollingTriggerInterface {

    String PLUGIN_PROPERTY_RECOVER_MISSED_SCHEDULES = "recoverMissedSchedules";

    @TimezoneId
    @Schema(
        title = "The [time zone identifier](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) (i.e. the second column in [the Wikipedia table](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones#List)) to use for scheduling the trigger. Default value is the system time-zone."
    )
    String getTimezone();

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
            .map(RecoverMissedSchedules::valueOf)
            .orElse(RecoverMissedSchedules.ALL);
    }
}
