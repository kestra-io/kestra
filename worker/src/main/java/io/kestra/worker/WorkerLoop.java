package io.kestra.worker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for implementing a worker loop that continuously processes jobs.
 * <p>
 * This class provides a thread-safe loop execution framework with pause/resume and graceful shutdown capabilities.
 * Subclasses must implement the {@link #doOnLoop()} method to define the work to be performed in each iteration.
 * <p>
 */
public abstract class WorkerLoop implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerLoop.class);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CountDownLatch stopped = new CountDownLatch(1);

    private final AtomicBoolean paused = new AtomicBoolean(false);

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();

    private final String name;

    /**
     * Creates a new {@link WorkerLoop} instance.
     *
     * @param name the name of the loop.
     */
    public WorkerLoop(final String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    /**
     * The name of the loop.
     *
     * @return the name of the loop.
     */
    public String name() {
        return name;
    }

    /**
     * Executes the main worker loop.
     * <p>
     * This method continuously calls {@link #doOnLoop()} while the loop is running.
     * The loop respects pause requests and will block until resumed.
     * Any exceptions thrown by {@link #doOnLoop()} are caught and logged, allowing the loop to continue.
     * <p>
     * The loop terminates when:
     * <ul>
     * <li>The {@link #stop(Duration)} method is called</li>
     * <li>An {@link InterruptedException} is thrown</li>
     * </ul>
     * <p>
     * When the loop exits, the {@link #cleanup()} hook is invoked.
     * <p>
     * This method should typically be executed in a dedicated thread.
     */
    @Override
    public void run() {
        try {
            runLoop();
        } finally {
            try {
                cleanup();
            } catch (Exception e) {
                LOG.error("Unexpected error while cleaning up worker loop", e);
            }
            stopped.countDown();
            LOG.debug("[{}] stopped", getClass().getSimpleName());
        }
    }

    /**
     * Main loop execution logic.
     */
    private void runLoop() {
        while (running.get()) {
            try {
                waitIfPaused();

                // Check if the WorkerIO thread was stopped while being paused
                if (!running.get()) {
                    continue;
                }

                doOnLoop();
            } catch (InterruptedException ie) {
                LOG.info("[{}] interrupted, stopping", getClass().getSimpleName());
                Thread.currentThread().interrupt();
                break; // exit loop
            } catch (Exception e) {
                LOG.error("Error in loop", e);
            }
        }
    }

    /**
     * Performs one iteration of the worker loop.
     * <p>
     * This method is called repeatedly while the loop is running and not paused.
     * Implementations should perform a single unit of work and return promptly to allow
     * the loop to check for pause or stop signals.
     * <p>
     * If this method throws an {@link InterruptedException}, the loop will terminate.
     * Other exceptions are caught, logged, and do not stop the loop execution.
     *
     * @throws Exception if an error occurs during loop execution
     */
    protected abstract void doOnLoop() throws Exception;

    /**
     * Cleanup hook invoked when the worker loop is stopping.
     * <p>
     * This method is called from the {@link #run()} method's finally block, ensuring it executes
     * regardless of how the loop terminates (normal stop, interrupt, or exception).
     * <p>
     * Subclasses can override this method to perform cleanup operations such as
     * closing resources, flushing buffers, or releasing locks.
     * <p>
     * The default implementation does nothing.
     * <p>
     * Any exceptions thrown by this method are caught and logged but do not prevent loop termination.
     *
     * @throws Exception if an error occurs during cleanup
     */
    protected void cleanup() throws Exception {
        // noop - subclasses override to release resources
    }

    private void waitIfPaused() throws InterruptedException {
        if (!paused.get()) {
            return; // return immediately
        }
        pauseLock.lock();
        try {
            while (paused.get() && running.get()) {
                LOG.debug("Paused. Waiting for worker loop to resume");
                unpaused.await(); // Wait until resume() signals
                LOG.debug("Resumed");
            }
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Pauses the WorkerLoop.
     * <p>
     * When paused, the loop will block until {@link #resume()} is called.
     * This method is thread-safe and can be called multiple times.
     */
    public void pause() {
        pauseLock.lock();
        try {
            paused.set(true);
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Resumes the WorkerLoop if it was previously paused.
     * <p>
     * This method signals the waiting loop to continue execution.
     * If the loop is not paused, this method has no effect.
     * This method is thread-safe.
     */
    public void resume() {
        pauseLock.lock();
        try {
            if (paused.compareAndSet(true, false)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Stops the WorkerLoop.
     * <p>
     * This method returns immediately without awaiting the worker loop to be effectively stopped
     */
    public void stop() {
        stop(Duration.ZERO);
    }

    /**
     * Stops the WorkerLoop.
     * 
     * <p>
     * If the loop is not running, this method returns immediately without any action.
     * If the loop does not complete within the timeout, a warning is logged, but the method returns normally.
     */
    public void stop(Duration timeout) {
        if (!running.compareAndSet(true, false)) {
            LOG.debug("[{}] stop() called but not running", getClass().getSimpleName());
            return;
        }

        resume();
        signalJobStop();

        if (timeout == null || timeout.isZero()) {
            return;
        }

        try {
            if (!stopped.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Timeout while waiting for {} to complete", name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Hook invoked before waiting for the loop to terminate.
     * <p>
     * Subclasses can override this to signal any currently executing job or operation
     * to stop gracefully before the loop shuts down.
     * <p>
     * This is called after the running flag is set to false but before waiting for
     * the loop thread to complete.
     */
    protected void signalJobStop() {
        // noop - subclasses override to signal their job processors
    }

    public boolean isRunning() {
        return running.get();
    }
}
