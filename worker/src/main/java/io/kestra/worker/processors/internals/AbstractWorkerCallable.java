package io.kestra.worker.processors.internals;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.models.WorkerJobLifecycle;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Exceptions;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

import static io.kestra.core.models.flows.State.Type.*;

@SuppressWarnings("this-escape")
public abstract class AbstractWorkerCallable implements Callable<State.Type> {
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "worker-job-timeout");
        thread.setDaemon(true);
        return thread;
    });

    /** The state to report once interrupted, or {@code null} if not interrupted (or interrupted without marking, e.g. on timeout). */
    volatile State.Type killedState = null;

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
        this.kill(KILLED);
    }

    /** {@inheritDoc} **/
    @Override
    public State.Type call() {
        this.currentThread = Thread.currentThread();
        this.currentThread.setContextClassLoader(classLoader);

        try {
            // Guard against a kill received before currentThread was recorded:
            // interrupt() was a no-op, so honor the killedState flag here.
            if (this.killedState != null) {
                return this.killedState;
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

    /**
     * Interrupts the running job.
     * If {@code state} is non-null, the job is marked to eventually report that state as its outcome
     * instead of the one {@link #doCall()} would otherwise produce.
     */
    public void kill(State.Type state) {
        this.killedState = state;

        // When we arrive here, the thread run() method may be ended but the thread "in the stopping process".
        // So we don't interrupt if the shutdownLatch is 0 as this means the run() method is done or if the thread is no more alive.
        if (shutdownLatch.getCount() > 0) {
            this.interrupt();
        }
    }

    protected State.Type exceptionHandler(Throwable e) {
        this.exception = e;
        Span.current().recordException(e).setStatus(StatusCode.ERROR);

        if (this.killedState != null) {
            return this.killedState;
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
     * Runs {@code work} bounded by {@code timeout} ({@code null} = unbounded).
     * <p>
     * When the timeout fires, {@link #kill(State.Type)} is invoked immediately on a watchdog
     * thread with a {@code null} state so the job reports {@code FAILED} rather than {@code KILLED}.
     * Subclass {@code kill()} also runs plugin {@code kill()}; interrupt alone does not unblock
     * some I/O (e.g. {@code KafkaConsumer.poll()}). Completing within the timeout returns {@code null}.
     *
     * @param onTimeout action to run when the timeout fires; may be {@code null}
     * @return the terminal state on timeout, or {@code null} if {@code work} completed in time
     */
    protected State.Type callWithTimeout(Duration timeout, Evaluation work, Runnable onTimeout) throws Exception {
        if (timeout == null) {
            work.run();
            return null;
        }

        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (timedOut.compareAndSet(false, true)) {
                try {
                    if (onTimeout != null) {
                        onTimeout.run();
                    }
                } finally {
                    kill((State.Type) null);
                }
            }
        }, timeout.toNanos(), TimeUnit.NANOSECONDS);

        try {
            work.run();
        } catch (Exception e) {
            if (timedOut.get()) {
                Thread.interrupted();
                return this.exceptionHandler(new TimeoutExceededException(timeout));
            }
            throw e;
        } finally {
            timeoutTask.cancel(false);
        }

        if (timedOut.get()) {
            Thread.interrupted();
            return this.exceptionHandler(new TimeoutExceededException(timeout));
        }
        return null;
    }

    public void interrupt() {
        if (this.currentThread != null && this.currentThread.isAlive()) {
            this.currentThread.interrupt();
        }
    }
}
