package io.kestra.worker;

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

    @Getter
    Optional<Execution> evaluate;

    WorkerTriggerCallable(RunContext runContext, WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        super(runContext, pollingTrigger.getClass().getName(), workerTrigger);
        this.pollingTrigger = pollingTrigger;
    }

    @Override
    public State.Type doCall() throws Exception {
        this.evaluate = this.pollingTrigger.evaluate(
            workerTrigger.getConditionContext().withRunContext(runContext),
            workerTrigger.getTriggerContext()
        );
        return SUCCESS;
    }
}
