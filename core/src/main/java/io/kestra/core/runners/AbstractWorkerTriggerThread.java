package io.kestra.core.runners;

import lombok.Getter;

public abstract class AbstractWorkerTriggerThread extends AbstractWorkerThread {
    @Getter
    WorkerTrigger workerTrigger;

    public AbstractWorkerTriggerThread(RunContext runContext, String type, WorkerTrigger workerTrigger) {
        super(runContext, type);
        this.workerTrigger = workerTrigger;
    }
}
