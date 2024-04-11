package io.kestra.core.runners;

import lombok.Getter;
import lombok.Synchronized;
import org.slf4j.Logger;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.KILLED;

public class AbstractWorkerThread extends Thread {
    volatile boolean killed = false;

    Logger logger;

    @Getter
    io.kestra.core.models.flows.State.Type taskState;

    @Getter
    Throwable exception;

    public AbstractWorkerThread(Logger logger) {
        super("WorkerThread");
        this.setUncaughtExceptionHandler(this::exceptionHandler);

        this.logger = logger;
    }

    @Synchronized
    public void kill() {
        this.killed = true;
        taskState = KILLED;
        this.interrupt();
    }

    protected void exceptionHandler(Thread t, Throwable e) {
        this.exception = e;

        if (!this.killed) {
            logger.error(e.getMessage(), e);
            taskState = FAILED;
        }
    }
}
