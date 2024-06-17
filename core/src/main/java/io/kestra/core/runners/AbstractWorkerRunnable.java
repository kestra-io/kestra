package io.kestra.core.runners;

import io.kestra.core.models.WorkerJobLifecycle;
import io.kestra.core.models.flows.State;
import lombok.Getter;
import lombok.Synchronized;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.KILLED;

public abstract class AbstractWorkerRunnable implements Callable<State.Type> {
    volatile boolean killed = false;

    Logger logger;

    @Getter
    RunContext runContext;

    @Getter
    String type;

    State.Type taskState;

    @Getter
    Throwable exception;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private final ClassLoader classLoader;

    private Thread currentThread;

    public AbstractWorkerRunnable(RunContext runContext, String type, ClassLoader classLoader) {
        this.logger = runContext.logger();
        this.runContext = runContext;
        this.type = type;
        this.classLoader = classLoader;
    }

    @Synchronized
    public void kill() {
        this.kill(true);
    }

    /** {@inheritDoc} **/
    @Override
    public State.Type call() {
        this.currentThread = Thread.currentThread();
        this.currentThread.setContextClassLoader(classLoader);

        try {
            this.taskState = doCall();
        } catch (Exception e) {
            this.exceptionHandler(e);
        } finally {
            shutdownLatch.countDown();
        }
        return this.taskState;
    }

    protected abstract State.Type doCall() throws Exception;

    /**
     * Signals to the job executed by this worker thread to stop.
     *
     * @see WorkerJobLifecycle#stop()
     */
    protected abstract void signalStop();

    /**
     * Wait for this worker task to complete stopping.
     *
     * @param timeout duration to await stop
     * @return {@code true} if successful, otherwise {@code true} if the timeout was reached.
     */
    public boolean awaitStop(final Duration timeout) {
        try {
            return shutdownLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    protected void kill(boolean markAsKilled) {
        this.killed = markAsKilled;
        this.taskState = KILLED;
        interrupt();
    }

    protected void exceptionHandler(Throwable e) {
        this.exception = e;

        if (!this.killed) {
            logger.error(e.getMessage(), e);
            taskState = FAILED;
        }
    }

    public void interrupt() {
        if (this.currentThread != null) {
            this.currentThread.interrupt();
        }
    }
}
