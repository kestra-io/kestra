package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import lombok.Getter;
import lombok.Synchronized;
import org.slf4j.Logger;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.KILLED;

public abstract class AbstractWorkerThread extends Thread {
    volatile boolean killed = false;

    Logger logger;

    @Getter
    RunContext runContext;

    @Getter
    String type;

    @Getter
    io.kestra.core.models.flows.State.Type taskState;

    @Getter
    Throwable exception;

    public AbstractWorkerThread(RunContext runContext, String type) {
        super("WorkerThread");
        this.setUncaughtExceptionHandler(this::exceptionHandler);

        this.logger = runContext.logger();
        this.runContext = runContext;
        this.type = type;
    }

    @Synchronized
    public void kill() {
        this.kill(true);
    }
    
    protected void kill(boolean markAsKilled) {
        this.killed = markAsKilled;
        this.taskState = KILLED;
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
