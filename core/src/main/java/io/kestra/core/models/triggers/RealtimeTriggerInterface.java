package io.kestra.core.models.triggers;

import org.reactivestreams.Publisher;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import reactor.core.publisher.Flux;

public interface RealtimeTriggerInterface extends WorkerTriggerInterface {

    /**
     * Evaluate the trigger and produce a stream of lightweight results.
     * <p>
     * New plugins should override this method. The default bridges to the
     * deprecated {@link #evaluate} for backward compatibility.
     */
    default Publisher<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) throws Exception {
        return Flux.from(evaluate(conditionContext, context)).map(TriggerEvaluationResult::from);
    }

    /**
     * @deprecated Override {@link #eval} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    default Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        throw new UnsupportedOperationException("Implement eval() instead of evaluate()");
    }
}
