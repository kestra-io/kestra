package io.kestra.scheduler.models;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.model.TriggerState;

/**
 * Represents the context data required to evaluate a trigger is ready to be scheduled.
 *
 * <p>The {@code TriggerScheduleContext} provides access to the
 * associated flow, trigger definition, and any supporting contexts
 * used during the evaluation.</p>
 *
 * @param flow             the flow that owns the trigger
 * @param trigger          the trigger being evaluated
 * @param triggerState     the trigger state
 * @param conditionContext the condition context used for trigger conditions
 */
public record TriggerEvaluationContext(
    FlowInterface flow,
    AbstractTrigger trigger,
    TriggerState triggerState,
    ConditionContext conditionContext
) {
    
}
