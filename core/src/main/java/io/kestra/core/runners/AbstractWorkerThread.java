package io.kestra.core.runners;

import io.kestra.core.models.WorkerJobLifecycle;
import lombok.Getter;
import lombok.Synchronized;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private final ClassLoader classLoader;

    public AbstractWorkerThread(RunContext runContext, String type, ClassLoader classLoader) {
        super("WorkerThread");
        this.setUncaughtExceptionHandler(this::exceptionHandler);

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
    public void run() {
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            doRun();
        } catch (Exception e) {
            this.exceptionHandler(this, e);
        } finally {
            shutdownLatch.countDown();
        }
    }

    protected abstract void doRun() throws Exception;

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
