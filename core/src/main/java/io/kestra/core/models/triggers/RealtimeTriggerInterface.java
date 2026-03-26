package io.kestra.core.models.triggers;

import org.reactivestreams.Publisher;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;

public interface RealtimeTriggerInterface extends WorkerTriggerInterface {
    Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception;
}
