package io.kestra.scheduler;

import io.kestra.scheduler.models.TriggerEvaluationContext;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Service interface for fetching schedulable triggers.
 */
public interface SchedulableTriggerFetcher {
    
    /**
     * Finds all trigger which must be scheduled as of the specified timestamp.
     * 
     * @param clock       the scheduler clock.
     * @param now         the current schedule time.
     * @param assignments the vNodes assignments.
     * @return the list of schedulable triggers.
     */
    List<TriggerEvaluationContext> getSchedulableTriggers(
        final Clock clock, final ZonedDateTime now, final Set<Integer> assignments);
}
