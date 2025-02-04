package io.kestra.core.runners;

import io.kestra.core.models.WorkerJobLifecycle;
import io.kestra.core.models.flows.State;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.Getter;
import lombok.Synchronized;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.models.flows.State.Type.*;

@SuppressWarnings("this-escape")
public abstract class AbstractWorkerCallable implements Callable<State.Type> {
    volatile boolean killed = false;

    Logger logger;

    @Getter
    RunContext runContext;

    @Getter
    String type;

    @Getter
    String uid;

    @Getter
    Throwable exception;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private final ClassLoader classLoader;

    private Thread currentThread;

    AbstractWorkerCallable(RunContext runContext, String type, String uid, ClassLoader classLoader) {
        this.logger = runContext.logger();
        this.runContext = runContext;
        this.type = type;
        this.uid = uid;
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
            return doCall();
        } catch (Throwable e) {
            // Catching Throwable is usually a bad idea.
            // However, here, we want to be sure that the task fails whatever happens,
            // and some plugins may throw errors, for example, for dependency issues or worst,
            // bad behavior that throws errors and not exceptions.
            return this.exceptionHandler(e);
        } finally {
            shutdownLatch.countDown();
        }
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

        // When we arrive here, the thread run() method may be ended but the thread "in the stopping process".
        // So we don't interrupt if the shutdownLatch is 0 as this means the run() method is done or if the thread is no more alive.
        if (shutdownLatch.getCount() > 0) {
            this.interrupt();
        }
    }

    protected State.Type exceptionHandler(Throwable e) {
        this.exception = e;
        Span.current().recordException(e).setStatus(StatusCode.ERROR);

        if (this.killed) {
            return KILLED;
        } else {
            logger.error(e.getMessage(), e);
            return FAILED;
        }
    }

    public void interrupt() {
        if (this.currentThread != null && this.currentThread.isAlive()) {
            this.currentThread.interrupt();
        }
    }
}
