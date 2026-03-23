package io.kestra.worker.processors.internals;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;
import lombok.Getter;

import java.util.Optional;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerCallable extends AbstractWorkerTriggerCallable {
    PollingTriggerInterface pollingTrigger;
    ConditionContext conditionContext;

    @Getter
    Optional<Execution> evaluate;

    public WorkerTriggerCallable(RunContext runContext, ConditionContext conditionContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        super(runContext, pollingTrigger.getClass().getName(), workerTrigger);
        this.pollingTrigger = pollingTrigger;
        this.conditionContext = conditionContext;
    }

    @Override
    public State.Type doCall() throws Exception {
        this.evaluate = this.pollingTrigger.evaluate(
            conditionContext.withRunContext(runContext),
            workerTrigger.getTriggerContext()
        );
        return SUCCESS;
    }
}
