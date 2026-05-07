package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.scheduler.model.TriggerState;

public final class RecoverMissedSchedule {
    private RecoverMissedSchedule() {
    }

    public static TriggerState apply(
        Clock clock,
        TriggerState currentTriggerState,
        AbstractTrigger trigger,
        Schedulable schedulableTrigger,
        ConditionContext conditionContext,
        RecoverMissedSchedules recoverMissedSchedules
    ) throws Exception {
        return switch (recoverMissedSchedules) {
            case LAST -> recoverLast(clock, currentTriggerState, schedulableTrigger, conditionContext);
            case NONE -> recoverNone(clock, currentTriggerState, trigger, conditionContext);
            case ALL -> currentTriggerState;
        };
    }

    private static TriggerState recoverLast(
        Clock clock,
        TriggerState currentTriggerState,
        Schedulable schedulableTrigger,
        ConditionContext conditionContext
    ) throws Exception {
        ZonedDateTime previousDate = schedulableTrigger.previousEvaluationDate(conditionContext);
        Instant evaluatedAt = currentTriggerState.getEvaluatedAt();

        if (evaluatedAt == null || previousDate.toInstant().isAfter(evaluatedAt)) {
            return currentTriggerState.updateForNextEvaluationDate(clock, previousDate);
        }

        return currentTriggerState;
    }

    private static TriggerState recoverNone(
        Clock clock,
        TriggerState currentTriggerState,
        AbstractTrigger trigger,
        ConditionContext conditionContext
    ) throws Exception {
        ZonedDateTime nextEvaluationDate = NextEvaluationDate.get(clock, trigger, null, conditionContext);
        if (!Objects.equals(currentTriggerState.getNextEvaluationDate(), nextEvaluationDate.toInstant())) {
            return currentTriggerState.updateForNextEvaluationDate(clock, nextEvaluationDate);
        }

        return currentTriggerState;
    }
}
