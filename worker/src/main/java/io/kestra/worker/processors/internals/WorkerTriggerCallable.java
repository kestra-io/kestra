package io.kestra.worker.processors.internals;

import java.util.Optional;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.models.triggers.TriggerEvaluationResult;

import lombok.Getter;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerCallable extends AbstractWorkerTriggerCallable {
    PollingTriggerInterface pollingTrigger;
    ConditionContext conditionContext;
    TriggerContext triggerContext;

    @Getter
    Optional<TriggerEvaluationResult> evaluate;

    public WorkerTriggerCallable(RunContext runContext, ConditionContext conditionContext, TriggerContext triggerContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        super(runContext, pollingTrigger.getClass().getName(), workerTrigger);
        this.pollingTrigger = pollingTrigger;
        this.conditionContext = conditionContext;
        this.triggerContext = triggerContext;
    }

    @Override
    public State.Type doCall() throws Exception {
        this.evaluate = this.pollingTrigger.eval(
            conditionContext.withRunContext(runContext),
            triggerContext
        );
        return SUCCESS;
    }
}
