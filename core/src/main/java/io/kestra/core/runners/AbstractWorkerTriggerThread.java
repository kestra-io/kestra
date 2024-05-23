package io.kestra.core.runners;

import io.kestra.core.models.triggers.WorkerTriggerInterface;
import lombok.Getter;

public abstract class AbstractWorkerTriggerThread extends AbstractWorkerThread {
    @Getter
    WorkerTrigger workerTrigger;

    public AbstractWorkerTriggerThread(RunContext runContext, String type, WorkerTrigger workerTrigger) {
        super(runContext, type, workerTrigger.getTrigger().getClass().getClassLoader());
        this.workerTrigger = workerTrigger;
    }

    @Override
    public void signalStop() {
        try {
            ((WorkerTriggerInterface)workerTrigger.getTrigger()).stop();
        } catch (Exception e) {
            logger.warn("Error while stopping trigger: '{}'", getType(), e);
        }
    }
}
