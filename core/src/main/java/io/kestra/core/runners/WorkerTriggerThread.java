package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import lombok.Getter;

import java.util.Optional;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerThread extends AbstractWorkerTriggerThread {
    PollingTriggerInterface pollingTrigger;

    @Getter
    Optional<Execution> evaluate;

    public WorkerTriggerThread(WorkerTrigger workerTrigger, PollingTriggerInterface pollingTrigger) {
        super(workerTrigger.getConditionContext().getRunContext(), pollingTrigger.getClass().getName(), workerTrigger);
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
