package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import lombok.Getter;

import java.util.Optional;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerThreadTrigger extends AbstractWorkerThread {
    RunContext runContext;
    PollingTriggerInterface pollingTrigger;

    @Getter
    WorkerTrigger workerTrigger;

    @Getter
    Optional<Execution> evaluate;

    public WorkerThreadTrigger(WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        super(workerTrigger.getConditionContext().getRunContext().logger());
        this.runContext = workerTrigger.getConditionContext().getRunContext();
        this.workerTrigger = workerTrigger;
        this.pollingTrigger = pollingTrigger;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(this.pollingTrigger.getClass().getClassLoader());

        try {
            this.evaluate = this.pollingTrigger.evaluate(
                workerTrigger.getConditionContext().withRunContext(runContext),
                workerTrigger.getTriggerContext()
            );
            taskState = SUCCESS;
        } catch (Exception e) {
            this.exceptionHandler(this, e);
        }
    }
}
