package io.kestra.webserver.services.ai;

import io.kestra.core.utils.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The period an AI spend ceiling is counted over, and at whose boundary it starts again.
 *
 * <p>Calendar periods rather than a rolling duration, so a ceiling is something a user can be told: spend
 * counted over the last thirty days frees up a call at a time and never resets, which leaves "when can I use
 * this again?" with no answer worth giving. A period that ends on a date has one.
 *
 * <p>Boundaries are UTC. A ceiling is an installation-wide accounting period, not a personal calendar, and
 * anchoring it to a viewer's zone would have the same window start at different instants for different callers.
 * What a user is shown is still rendered in their own zone.
 */
public enum AiUsageWindow {
    /** Since midnight. */
    DAILY,

    /** Since Monday, which is the ISO week's first day. */
    WEEKLY,

    /** Since the first of the month. */
    MONTHLY;

    /**
     * These names are also the contract with the hosted relay, which serves one of them from {@code /limits}.
     * Anything else fails rather than falling back, because a period nobody can name is one nobody configured
     * on purpose — and silently substituting a default would enforce a ceiling over a period the operator did
     * not choose.
     */
    @JsonCreator
    public static AiUsageWindow fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, AiUsageWindow.class);
    }

    /** The instant the current period began, which is what totals are summed from. */
    public Instant start(final Instant now) {
        return startAt(now).toInstant();
    }

    /** The instant the current period ends and the ceiling is clear again. */
    public Instant next(final Instant now) {
        ZonedDateTime start = startAt(now);
        return switch (this) {
            case DAILY -> start.plusDays(1).toInstant();
            case WEEKLY -> start.plusWeeks(1).toInstant();
            case MONTHLY -> start.plusMonths(1).toInstant();
        };
    }

    private ZonedDateTime startAt(final Instant now) {
        ZonedDateTime day = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        return switch (this) {
            case DAILY -> day;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> day.withDayOfMonth(1);
        };
    }
}
