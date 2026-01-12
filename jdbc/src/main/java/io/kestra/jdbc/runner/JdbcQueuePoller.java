package io.kestra.jdbc.runner;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class responsible for continuously executing a polling query.
 */
public final class JdbcQueuePoller implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(JdbcQueuePoller.class);

    private final JdbcQueueConfiguration configuration;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Callable<Integer> pollingQuery;

    // Pause
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();

    private final CountDownLatch stopped = new CountDownLatch(1);

    /**
     * Creates a new {@link JdbcQueuePoller} instance.
     *
     * @param configuration the {@link JdbcQueueConfiguration}.
     * @param pollingQuery         the query to be executed.
     */
    public JdbcQueuePoller(final JdbcQueueConfiguration configuration,
                           final Callable<Integer> pollingQuery) {
        this.configuration = Objects.requireNonNull(configuration);
        this.pollingQuery = Objects.requireNonNull(pollingQuery);
    }

    @Override
    public void run() {
        List<JdbcQueueConfiguration.Step> steps = configuration.computeSteps();
        ZonedDateTime lastPoll = ZonedDateTime.now();
        try {
            while (running.get()) {
                ZonedDateTime poll = pollOnce(lastPoll, steps);
                if (poll != null) {
                    lastPoll = poll;
                }
            }
        } finally {
            stopped.countDown();
        }
    }

    @VisibleForTesting
    ZonedDateTime pollOnce(ZonedDateTime lastPoll, List<JdbcQueueConfiguration.Step> steps) {
        Duration sleep;
        try {
            // Check pause before starting any query
            waitIfPaused();

            // Check if the loop was stopped while being paused
            if (!running.get()) {
                return null;
            }

            Integer count = pollingQuery.call();
            if (count > 0) {
                lastPoll = ZonedDateTime.now();
                sleep = configuration.minPollInterval();
                if (configuration.immediateRepoll()) {
                    return lastPoll;
                } else if (count.equals(configuration.pollSize())) {
                    // Note: this provides better latency on high throughput: when Kestra is a top capacity,
                    // it will not do a sleep and immediately poll again.
                    // We can even have better latency at even higher latency by continuing for positive count,
                    // but at higher database cost.
                    // Current impl balance database cost with latency.
                    return lastPoll;
                }
            } else {
                ZonedDateTime finalLastPoll = lastPoll;
                // get all poll steps which duration is less than the duration between last poll and now
                List<JdbcQueueConfiguration.Step> selectedSteps = steps.stream()
                    .takeWhile(step -> finalLastPoll.plus(step.switchInterval()).compareTo(ZonedDateTime.now()) < 0)
                    .toList();
                // then select the last one (longest) or minPoll if all are beyond while means we are under the first interval
                sleep = selectedSteps.isEmpty() ? configuration.minPollInterval() : selectedSteps.getLast().pollInterval();
            }

            Thread.sleep(sleep);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting. Stopping.");
            running.set(false);
        } catch (CannotCreateTransactionException e) {
            if (log.isDebugEnabled()) {
                log.debug("Can't poll on receive", e);
            }
        } catch (Exception e) {
            throw new KestraRuntimeException("Unexpected error while executing queue polling query", e);
        }
        return lastPoll;
    }

    private void waitIfPaused() throws InterruptedException {
        if (!paused.get()) {
            return; // return immediately if not paused.
        }

        pauseLock.lock();
        try {
            while (paused.get() && running.get()) {
                log.debug("Paused. Waiting for {} to resume", JdbcQueuePoller.class.getSimpleName());
                unpaused.await(); // Wait until resume() signals
                log.debug("Resumed");
            }
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Pauses this poller.
     */
    public void pause() {
        paused.set(true);
    }

    /**
     * Resumes this poller if currently paused.
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
     * Stops this poller.
     */
    public void stop() {
        if (!this.running.compareAndSet(true, false)) {
            return; // already stopped
        }

        resume(); // In case it's paused and blocked

        try {
            // wait for the poller to be stooped
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for {} to be stopped.", this.getClass().getSimpleName());
        }
    }
}
