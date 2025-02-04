package io.kestra.core.runners;

import io.kestra.core.models.triggers.WorkerTriggerInterface;
import lombok.Getter;

import java.time.Duration;

abstract class AbstractWorkerTriggerCallable extends AbstractWorkerCallable {
    // This duration is by design low as we don't want to hang a thread for too long when we kill a trigger
    private static final Duration AWAIT_ON_KILL = Duration.ofMillis(50);

    @Getter
    WorkerTrigger workerTrigger;

    AbstractWorkerTriggerCallable(RunContext runContext, String type, WorkerTrigger workerTrigger) {
        super(runContext, type, workerTrigger.uid(), workerTrigger.getTrigger().getClass().getClassLoader());
        this.workerTrigger = workerTrigger;
    }

    @Override
    public void signalStop() {
        try {
            ((WorkerTriggerInterface) workerTrigger.getTrigger()).stop();
        } catch (Exception e) {
            logger.warn("Error while stopping trigger: '{}'", getType(), e);
        }
    }

    @Override
    protected void kill(final boolean markAsKilled) {
        try {
            ((WorkerTriggerInterface) workerTrigger.getTrigger()).kill();
            if (markAsKilled) {
                // Let some time for the target thread to end, so we have a chance to not have to interrupt it.
                // Killing a trigger is part of normal operations (updating a flow, disabling it, restarting Kestra),
                // so we want to have a chance to end them properly and release their resources (transactions for ex).
                awaitStop(AWAIT_ON_KILL);
            }
        } catch (Exception e) {
            logger.warn("Error while killing trigger: '{}'", getType(), e);
        } finally {
            super.kill(markAsKilled); //interrupt
        }
    }
}
