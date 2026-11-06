package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.event.Level;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.utils.Logs;
import io.kestra.scheduler.models.TriggerEvaluationContext;

/**
 * Decides which schedules a trigger catches up on when it resumes after a pause, such as a re-enable.
 */
public final class TriggerResumer {

    private TriggerResumer() {
    }

    /**
     * Updates the state of a resumed trigger: missed schedules are recovered according to the trigger's own
     * {@code recoverMissedSchedules} configuration. The configuration is always re-resolved (never overridden per
     * action) so that this decision and the scheduler startup recovery ({@code TriggerScheduler#onStart}) stay
     * consistent across restarts and vNode re-assignments.
     */
    public static TriggerState resume(Clock clock, TriggerEvaluationContext context) {
        return switch (resolveRecoverMissedSchedules(context)) {
            // Keep the frozen past nextEvaluationDate so the scheduling loop replays each missed tick.
            case ALL -> context.triggerState().getNextEvaluationDate() != null
                ? context.triggerState()
                : context.triggerState().updateForNextEvaluationDate(clock, nextEvaluationDate(clock, context, context.triggerState().context()));
            case LAST -> updateForLastMissedSchedule(clock, context);
            case NONE -> skipMissed(clock, context);
        };
    }

    /**
     * Updates the state of a resumed trigger so that its missed schedules are skipped.
     */
    public static TriggerState skipMissed(Clock clock, TriggerEvaluationContext context) {
        // Also advance evaluatedAt so the startup recovery cannot resurrect the skipped schedules.
        return context.triggerState()
            .evaluatedAt(clock, ZonedDateTime.now(clock))
            .updateForNextEvaluationDate(clock, nextEvaluationDateSkippingMissed(clock, context));
    }

    private static RecoverMissedSchedules resolveRecoverMissedSchedules(TriggerEvaluationContext context) {
        if (!(context.trigger() instanceof Schedulable schedulable)) {
            return RecoverMissedSchedules.NONE;
        }
        return Optional.ofNullable(schedulable.getRecoverMissedSchedules())
            .orElseGet(() -> schedulable.defaultRecoverMissedSchedules(context.conditionContext().getRunContext()));
    }

    private static TriggerState updateForLastMissedSchedule(Clock clock, TriggerEvaluationContext context) {
        TriggerState state = context.triggerState();
        // A running backfill must resume untouched: recomputing the next evaluation date would re-derive
        // the backfill from the new date and silently drop or corrupt its remaining range.
        if (state.getBackfill() != null) {
            return state;
        }
        // Never evaluated (e.g. created disabled): there is no missed schedule to recover, and the last
        // cron tick before now could even predate the trigger's creation.
        if (state.getEvaluatedAt() == null) {
            return state.updateForNextEvaluationDate(clock, nextEvaluationDateSkippingMissed(clock, context));
        }
        try {
            Optional<ZonedDateTime> previousDate = ((Schedulable) context.trigger()).previousEvaluationDate(context.conditionContext());
            if (previousDate.isPresent() && previousDate.get().toInstant().isAfter(state.getEvaluatedAt())) {
                return state.updateForNextEvaluationDate(clock, previousDate.get());
            }
            // Nothing was missed: keep the stored next evaluation date.
            return state;
        } catch (IllegalVariableEvaluationException e) {
            Logs.logTrigger(state, Level.WARN, "Cannot compute the previous evaluation date, missed schedules will be skipped. Cause: {}", e.getMessage());
            return state.updateForNextEvaluationDate(clock, nextEvaluationDateSkippingMissed(clock, context));
        }
    }

    /**
     * Computes the next evaluation date of a resumed trigger so that missed schedules are skipped: the stale
     * evaluation date is dropped from the trigger context so the computation seeds from now, while a
     * running backfill is preserved so it resumes from its current date.
     */
    private static ZonedDateTime nextEvaluationDateSkippingMissed(Clock clock, TriggerEvaluationContext context) {
        TriggerContext triggerContext = context.triggerState().context().toBuilder().date(null).build();
        return nextEvaluationDate(clock, context, triggerContext);
    }

    private static ZonedDateTime nextEvaluationDate(Clock clock, TriggerEvaluationContext context, TriggerContext triggerContext) {
        return NextEvaluationDate.get(clock, context.trigger(), triggerContext, context.conditionContext());
    }
}
