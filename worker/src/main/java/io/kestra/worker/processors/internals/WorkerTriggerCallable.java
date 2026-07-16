package io.kestra.worker.processors.internals;

import java.time.Duration;
import java.util.Optional;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;

import lombok.Getter;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerCallable extends AbstractWorkerTriggerCallable {
    PollingTriggerInterface pollingTrigger;
    ConditionContext conditionContext;
    TriggerContext triggerContext;

    // Maximum time the evaluation is allowed to run before being interrupted; null disables the timeout.
    private final Duration triggerEvaluationTimeout;

    // Initialized to empty so getEvaluate() is never null when the evaluation ends
    // before the assignment (killed, timed out, or failed).
    @Getter
    Optional<TriggerEvaluationResult> evaluate = Optional.empty();

    public WorkerTriggerCallable(RunContext runContext, ConditionContext conditionContext, TriggerContext triggerContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger, Duration triggerEvaluationTimeout) {
        super(runContext, pollingTrigger.getClass().getName(), workerTrigger);
        this.pollingTrigger = pollingTrigger;
        this.conditionContext = conditionContext;
        this.triggerContext = triggerContext;
        this.triggerEvaluationTimeout = triggerEvaluationTimeout;
    }

    @Override
    public State.Type doCall() throws Exception {
        // The timeout metric is recorded by WorkerTriggerProcessor (which owns the metric tags).
        State.Type timeoutState = callWithTimeout(
            triggerEvaluationTimeout,
            () -> this.evaluate = this.pollingTrigger.eval(conditionContext.withRunContext(runContext), triggerContext),
            null
        );
        return timeoutState != null ? timeoutState : SUCCESS;
    }
}
