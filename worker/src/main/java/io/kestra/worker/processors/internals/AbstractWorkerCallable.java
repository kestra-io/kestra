package io.kestra.worker.processors.internals;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.models.WorkerJobLifecycle;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Exceptions;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

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
    @Setter
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
            // Guard against a kill received before currentThread was recorded:
            // interrupt() was a no-op, so honor the killed flag here.
            if (this.killed) {
                return KILLED;
            }
            return doCall();
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
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
    public abstract void signalStop();

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

    @FunctionalInterface
    protected interface Evaluation {
        void run() throws Exception;
    }

    /**
     * Runs {@code work} bounded by an interruptible {@code timeout} ({@code null} = unbounded).
     * <p>
     * On timeout the worker thread is interrupted to awake blocking calls (e.g. a stuck
     * {@code KafkaConsumer.poll()}), the interrupt flag is then cleared so it does not leak to later
     * operations, {@code onTimeout} is invoked (e.g. a metric increment), and the timeout is recorded
     * via {@link #exceptionHandler} — returning {@code FAILED} (or {@code KILLED}). When {@code work}
     * completes within the timeout this returns {@code null} and the caller maps its own success state.
     * Failsafe wraps checked exceptions thrown by {@code work}; the cause is unwrapped and rethrown so
     * callers handle it exactly as they would without a timeout.
     *
     * @param onTimeout action to run when the timeout fires; may be {@code null}
     * @return the terminal state on timeout, or {@code null} if {@code work} completed in time
     */
    protected State.Type callWithTimeout(Duration timeout, Evaluation work, Runnable onTimeout) throws Exception {
        if (timeout == null) {
            work.run();
            return null;
        }

        Timeout<Object> failsafeTimeout = Timeout
            .builder(timeout)
            .withInterrupt() // use to awake blocking evaluations, e.g. a stuck KafkaConsumer.poll().
            .build();
        try {
            Failsafe.with(failsafeTimeout).run(work::run);
            return null;
        } catch (dev.failsafe.TimeoutExceededException e) {
            if (onTimeout != null) {
                onTimeout.run();
            }
            kill(false);
            // Clear the interrupt flag set by Failsafe's withInterrupt() so it doesn't leak to the caller.
            Thread.interrupted();
            return this.exceptionHandler(new TimeoutExceededException(timeout));
        } catch (dev.failsafe.FailsafeException e) {
            // Failsafe wraps checked exceptions; unwrap so they are handled like a normal failure.
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    public void interrupt() {
        if (this.currentThread != null && this.currentThread.isAlive()) {
            this.currentThread.interrupt();
        }
    }
}
