package io.kestra.cli;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Prints a distinctive "Kestra is ready" banner as the <em>last</em> line in the terminal once the
 * server is fully started.
 *
 * <p>A running server starts several services on parallel threads (executor, worker, scheduler,
 * indexer, controller) that keep logging for a short while after the readiness state is reached.
 * Printing the banner synchronously right after startup therefore leaves it buried in the middle of
 * those async logs. To make the banner reliably the last visible line, this helper attaches a tiny
 * logback appender that records the timestamp of every log event, then waits until the log stream
 * has been quiet for {@code quietPeriodMillis} before printing. This is data-driven rather than a
 * fixed sleep: it adapts to how chatty the boot is, with {@code maxWaitMillis} as a safety cap so it
 * never blocks the command indefinitely.
 */
public final class ReadinessBanner {
    private ReadinessBanner() {
    }

    /**
     * Default time the log stream must stay silent before the banner is considered "last".
     *
     * <p>Several services finish booting on their own staggered timers — notably the liveness
     * coordinator, which elects a leader and kicks off trigger scheduling only a few seconds after
     * the other services report RUNNING. That late burst sits in a lull after the initial
     * "...started" lines, so the quiet window must be long enough to bridge it; otherwise the
     * banner prints in the gap and ends up buried again.
     */
    public static final long DEFAULT_QUIET_PERIOD_MILLIS = 5_000L;

    /**
     * Default safety cap on how long to wait for the log stream to go quiet.
     */
    public static final long DEFAULT_MAX_WAIT_MILLIS = 30_000L;

    private static final long POLL_INTERVAL_MILLIS = 50L;

    /**
     * Prints {@code message} once the application logs have been quiet for
     * {@link #DEFAULT_QUIET_PERIOD_MILLIS}, using the default safety cap.
     */
    public static void printWhenQuiet(String message) {
        printWhenQuiet(message, DEFAULT_QUIET_PERIOD_MILLIS, DEFAULT_MAX_WAIT_MILLIS);
    }

    /**
     * Prints {@code message} once the application logs have been quiet for at least
     * {@code quietPeriodMillis}, so the banner reliably appears as the last line in the terminal.
     * Falls back to printing after {@code maxWaitMillis} if the log stream never goes quiet.
     *
     * <p>The message is wrapped in blank lines and written to {@code stdout} (no logger prefix) so
     * the URL stands out and is auto-linkified by the terminal.
     */
    public static void printWhenQuiet(String message, long quietPeriodMillis, long maxWaitMillis) {
        AtomicLong lastLogNanos = new AtomicLong(System.nanoTime());

        AppenderBase<ILoggingEvent> tracker = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                lastLogNanos.set(System.nanoTime());
            }
        };

        Logger root = null;
        try {
            root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            tracker.setContext(root.getLoggerContext());
            tracker.start();
            root.addAppender(tracker);

            long deadlineNanos = System.nanoTime() + maxWaitMillis * 1_000_000L;
            long quietNanos = quietPeriodMillis * 1_000_000L;

            while (System.nanoTime() < deadlineNanos) {
                if (System.nanoTime() - lastLogNanos.get() >= quietNanos) {
                    break;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Banner is best-effort: never let it break server startup.
        } finally {
            if (root != null) {
                root.detachAppender(tracker);
            }
            tracker.stop();
        }

        System.out.println("\n" + message + "\n");
    }
}
