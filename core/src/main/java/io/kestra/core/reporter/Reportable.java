package io.kestra.core.reporter;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Interface for reporting server event for a specific type.
 *
 * @param <T>
 */
public interface Reportable<T extends Reportable.Event> {
    
    /**
     * Gets the type of the event to report.
     */
    Type type();
    
    /**
     * Gets the reporting schedule.
     */
    ReportingSchedule schedule();
    
    /**
     * Generates a report for the given timestamp.
     *
     * @param now the time when the report is triggered.
     * @return an Optional containing the report data if available.
     */
    T report(Instant now, TimeInterval interval);
    
    default T report(Instant now) {
        ZonedDateTime to = now.atZone(ZoneId.systemDefault());
        ZonedDateTime from = to.minus(Duration.ofDays(1));
        return report(now, new TimeInterval(from, to));
    }
    
    /**
     * Checks whether this reportable is enabled for scheduled reporting.
     */
    boolean isEnabled();
    
    /**
     * Generates a report for the given timestamp and tenant.
     *
     * @param now    the time when the report is triggered.
     * @param tenant the tenant for which the report is triggered.
     * @return the event to report.
     */
    default T report(Instant now, TimeInterval interval, String tenant) {
        throw new UnsupportedOperationException();
    }
    
    default T report(Instant now, String tenant) {
        ZonedDateTime to = now.atZone(ZoneId.systemDefault());
        ZonedDateTime from = to.minus(Duration.ofDays(1));
        return report(now, new TimeInterval(from, to), tenant);
    }
    
    /**
     * Checks whether this {@link Reportable} can accept a tenant.
     * 
     * @return {@code true} a {@link #report(Instant, TimeInterval, String)} can called, Otherwise {@code false}.
     */
    default boolean isTenantSupported() {
        return false;
    }
    
    record TimeInterval(ZonedDateTime from, ZonedDateTime to){
        public static TimeInterval of(ZonedDateTime from, ZonedDateTime to) {
            return new TimeInterval(from, to);
        }
    }
    
    /**
     * Marker interface indicating that the returned event 
     * must be a structured, domain-specific object 
     * (not a primitive wrapper, String, collection, or other basic type).
     */
    interface Event {
        
    }
    
    /**
     * Defines the schedule for a report.
     */
    interface ReportingSchedule {
        /**
         * Determines whether a report should run at the given instant.
         */
        boolean shouldRun(Instant now);
    }
}
