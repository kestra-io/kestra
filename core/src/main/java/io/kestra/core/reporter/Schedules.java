package io.kestra.core.reporter;

import io.kestra.core.reporter.Reportable.ReportingSchedule;

import java.time.Duration;
import java.time.Instant;

/**
 * Utility class providing common implementations of {@link Reportable.ReportingSchedule}.
 */
public class Schedules {
    
    /**
     * Creates a reporting schedule that triggers after the specified period has elapsed
     * since the last execution.
     *
     * @param period the duration between successive runs; must be positive
     * @return a {@link Reportable.ReportingSchedule} that runs at the given interval
     * @throws IllegalArgumentException if {@code period} is zero or negative
     */
    public static ReportingSchedule every(final Duration period) {
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Period must be positive");
        }
        
        return new ReportingSchedule() {
            private Instant lastRun = Instant.EPOCH;
            
            @Override
            public boolean shouldRun(Instant now) {
                if (Duration.between(lastRun, now).compareTo(period) >= 0) {
                    lastRun = now;
                    return true;
                }
                return false;
            }
        };
    }
    
    /**
     * Creates a reporting schedule that triggers once every hour.
     *
     * @return a schedule running every 1 hour
     */
    public static ReportingSchedule hourly() {
        return every(Duration.ofHours(1));
    }
    
    /**
     * Creates a reporting schedule that triggers once every day.
     *
     * @return a schedule running every 24 hours
     */
    public static ReportingSchedule daily() {
        return every(Duration.ofDays(1));
    }
}
