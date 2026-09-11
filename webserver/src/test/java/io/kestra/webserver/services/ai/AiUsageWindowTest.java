package io.kestra.webserver.services.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The period boundaries a ceiling is counted between, and the two spellings they are configured in. */
class AiUsageWindowTest {
    /** A Wednesday, mid-month, mid-afternoon — far enough from every boundary that each one has to be found. */
    private static final Instant WEDNESDAY = Instant.parse("2026-01-14T15:42:07Z");

    @Test
    void shouldCountTheDayFromMidnight() {
        assertThat(AiUsageWindow.DAILY.start(WEDNESDAY)).isEqualTo(Instant.parse("2026-01-14T00:00:00Z"));
        assertThat(AiUsageWindow.DAILY.next(WEDNESDAY)).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
    }

    @Test
    void shouldCountTheWeekFromMonday() {
        // The ISO week, so a ceiling turns over when a working week starts
        assertThat(AiUsageWindow.WEEKLY.start(WEDNESDAY)).isEqualTo(Instant.parse("2026-01-12T00:00:00Z"));
        assertThat(AiUsageWindow.WEEKLY.next(WEDNESDAY)).isEqualTo(Instant.parse("2026-01-19T00:00:00Z"));
    }

    @Test
    void shouldCountTheMonthFromItsFirstDay() {
        assertThat(AiUsageWindow.MONTHLY.start(WEDNESDAY)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(AiUsageWindow.MONTHLY.next(WEDNESDAY)).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void shouldFollowTheCalendarRatherThanAFixedNumberOfDays() {
        // Given February, which is neither thirty days nor the same length twice in four years
        Instant february = Instant.parse("2028-02-20T10:00:00Z");

        // Then the period ends when the month does; a fixed thirty days would drift off the calendar
        assertThat(AiUsageWindow.MONTHLY.next(february)).isEqualTo(Instant.parse("2028-03-01T00:00:00Z"));
    }

    @Test
    void shouldReadThePeriodNamesAnOperatorWrites() {
        assertThat(AiUsageWindow.fromString("DAILY")).isEqualTo(AiUsageWindow.DAILY);
        assertThat(AiUsageWindow.fromString("weekly")).isEqualTo(AiUsageWindow.WEEKLY);
        assertThat(AiUsageWindow.fromString("Monthly")).isEqualTo(AiUsageWindow.MONTHLY);
    }

    @Test
    void shouldRefuseAnythingItCannotName() {
        // Fails rather than falling back to a default that would meter against the wrong span.
        assertThatThrownBy(() -> AiUsageWindow.fromString("FORTNIGHTLY"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY, WEEKLY, MONTHLY");
    }

    @Test
    void shouldRefuseADurationSinceTheRelayOnlyEverNamesAPeriod() {
        // A duration was accepted while the relay stated one; it serves "DAILY" now, so a duration here is a typo.
        assertThatThrownBy(() -> AiUsageWindow.fromString("PT24H"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
