package io.kestra.core.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * A singleton utility class providing a configurable {@link Clock} instance
 * for scheduling or testing purposes.
 * <p>
 * By default, it uses the system default time zone. The clock can be replaced
 * with a fixed or offset clock, which is useful for testing time-dependent logic.
 * <p>
 * This class is thread-safe.
 */
public final class SchedulerClock {

    private static final SchedulerClock INSTANCE = new SchedulerClock();

    /**
     * The current clock instance used by this scheduler.
     * <p>
     * Declared as volatile to ensure visibility across threads.
     */
    private volatile Clock clock = Clock.systemDefaultZone();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private SchedulerClock() {
    }

    /**
     * Returns the current {@link Clock} instance used by this scheduler.
     *
     * @return the current clock
     */
    public static Clock getClock() {
        return INSTANCE.clock;
    }

    /**
     * Replaces the current clock with the given {@link Clock} instance.
     * <p>
     * This method is thread-safe.
     *
     * @param clock the new clock to set
     * @throws NullPointerException if {@code clock} is null
     */
    public static void setClock(final Clock clock) {
        if (clock == null) {
            throw new NullPointerException("clock must not be null");
        }
        INSTANCE.clock = clock;
    }

    /**
     * Updates the current clock with the specified duration added.
     *
     * @param duration the duration to add.
     */
    public static void offset(final Duration duration) {
        synchronized (INSTANCE) {
            INSTANCE.clock = Clock.offset(INSTANCE.clock, duration);
        }
    }

    /**
     * Returns the current {@link ZonedDateTime} according to the scheduler's clock.
     *
     * @return the current date-time
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(INSTANCE.clock);
    }
}