package io.kestra.core.models.triggers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    void shouldFallBackToSystemZoneWhenTimezoneIsNull() {
        // Given
        TimeWindow timeWindow = TimeWindow.builder().build();

        // When-Then
        assertThat(timeWindow.zoneId()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void shouldResolveConfiguredZone() {
        // Given
        TimeWindow timeWindow = TimeWindow.builder().timezone("Asia/Tokyo").build();

        // When-Then
        assertThat(timeWindow.zoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void shouldUseWallClockDeadlineWhenSpringForwardGivenEuropeParis() {
        // Given: 2025-03-30 is the Paris spring-forward day (23h calendar day)
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_DEADLINE)
            .deadline(LocalTime.of(9, 0))
            .timezone(PARIS.getId())
            .build();
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2025, 3, 30), LocalTime.of(0, 30), PARIS);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(now);

        // Then
        assertThat(boundaries.getRight().toInstant()).isEqualTo(Instant.parse("2025-03-30T07:00:00Z"));
        assertThat(boundaries.getRight().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(Duration.between(boundaries.getLeft(), boundaries.getRight())).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void shouldUseWallClockDeadlineWhenFallBackGivenEuropeParis() {
        // Given: 2025-10-26 is the Paris fall-back day (25h calendar day)
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_DEADLINE)
            .deadline(LocalTime.of(9, 0))
            .timezone(PARIS.getId())
            .build();
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2025, 10, 26), LocalTime.of(0, 30), PARIS);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(now);

        // Then
        assertThat(boundaries.getRight().toInstant()).isEqualTo(Instant.parse("2025-10-26T08:00:00Z"));
        assertThat(boundaries.getRight().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(Duration.between(boundaries.getLeft(), boundaries.getRight())).isEqualTo(Duration.ofHours(10));
    }

    @Test
    void shouldShiftForwardWhenDailyTimeFallsInDstGap() {
        // Given: 02:30 does not exist on the Paris spring-forward day
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_DEADLINE)
            .deadline(LocalTime.of(2, 30))
            .timezone(PARIS.getId())
            .build();
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2025, 3, 30), LocalTime.of(0, 0), PARIS);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(now);

        // Then
        assertThat(boundaries.getRight()).isEqualTo(ZonedDateTime.of(2025, 3, 30, 3, 30, 0, 0, PARIS));
    }

    @Test
    void shouldPickEarlierOffsetWhenDailyTimeIsAmbiguousRegardlessOfWhenCreateRuns() {
        // Given: 02:30 occurs twice on the Paris fall-back day
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_DEADLINE)
            .deadline(LocalTime.of(2, 30))
            .timezone(PARIS.getId())
            .build();
        ZonedDateTime beforeTransition = ZonedDateTime.of(LocalDate.of(2025, 10, 26), LocalTime.of(0, 10), PARIS);
        ZonedDateTime afterTransition = ZonedDateTime.of(LocalDate.of(2025, 10, 26), LocalTime.of(10, 0), PARIS);

        // When
        ZonedDateTime resolvedBefore = timeWindow.boundaries(beforeTransition).getRight();
        ZonedDateTime resolvedAfter = timeWindow.boundaries(afterTransition).getRight();

        // Then: same instant regardless of the offset `now` happened to carry
        assertThat(resolvedBefore.toInstant()).isEqualTo(Instant.parse("2025-10-26T00:30:00Z"));
        assertThat(resolvedAfter.toInstant()).isEqualTo(resolvedBefore.toInstant());
    }

    @Test
    void shouldSpanTwentyFiveHoursWhenDailyWindowCoversFallBack() {
        // Given
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_WINDOW)
            .startTime(LocalTime.MIDNIGHT)
            .endTime(LocalTime.of(23, 59, 59))
            .timezone(PARIS.getId())
            .build();
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2025, 10, 26), LocalTime.of(12, 0), PARIS);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(now);

        // Then
        assertThat(Duration.between(boundaries.getLeft(), boundaries.getRight())).isEqualTo(Duration.ofHours(24).plusMinutes(59).plusSeconds(59));
    }

    @Test
    void shouldAnchorDurationWindowToMidnightInConfiguredZone() {
        // Given: `now` is expressed in UTC, but the configured timezone is America/New_York
        ZoneId newYork = ZoneId.of("America/New_York");
        TimeWindow timeWindow = TimeWindow.builder()
            .type(TimeWindow.Type.DURATION_WINDOW)
            .window(Duration.ofDays(1))
            .timezone(newYork.getId())
            .build();
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2025, 6, 15), LocalTime.of(14, 30), ZoneId.of("UTC"));

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(now);

        // Then: the window is anchored to midnight in the configured zone, not in `now`'s own zone
        assertThat(boundaries.getLeft().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(boundaries.getLeft().getZone()).isEqualTo(newYork);
    }

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static ZonedDateTime utc(int day, int hour, int minute) {
        return ZonedDateTime.of(2025, 6, day, hour, minute, 0, 0, UTC);
    }

    private static TimeWindow durationWindow(Duration every, Duration offset) {
        return TimeWindow.builder()
            .type(TimeWindow.Type.DURATION_WINDOW)
            .window(every)
            .windowAdvance(offset)
            .timezone(UTC.getId())
            .build();
    }

    @Test
    void shouldStartDailyWindowYesterdayWhenNowIsBeforeOffset() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofDays(1), Duration.ofHours(9));

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 7, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(14, 9, 0));
        assertThat(boundaries.getRight()).isEqualTo(utc(15, 9, 0).minus(Duration.ofMillis(1)));
    }

    @Test
    void shouldStartDailyWindowTodayWhenNowIsAfterOffset() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofDays(1), Duration.ofHours(9));

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 10, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 9, 0));
        assertThat(boundaries.getRight()).isEqualTo(utc(16, 9, 0).minus(Duration.ofMillis(1)));
    }

    @Test
    void shouldStartDailyWindowAtMidnightWhenNoOffset() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofDays(1), null);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 7, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 0, 0));
        assertThat(boundaries.getRight()).isEqualTo(utc(16, 0, 0).minus(Duration.ofMillis(1)));
    }

    @Test
    void shouldKeepWindowStartingExactlyOnOffset() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofDays(1), Duration.ofHours(9));

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 9, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 9, 0));
    }

    @Test
    void shouldAlignSixHoursWindowOnStartOfDay() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofHours(6), null);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 7, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 6, 0));
        assertThat(boundaries.getRight()).isEqualTo(utc(15, 12, 0).minus(Duration.ofMillis(1)));
    }

    @Test
    void shouldAlignSixHoursWindowOnMidnightJustAfterMidnight() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofHours(6), null);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 0, 30));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 0, 0));
        assertThat(boundaries.getRight()).isEqualTo(utc(15, 6, 0).minus(Duration.ofMillis(1)));
    }

    @Test
    void shouldKeepSixHoursWindowStartingExactlyOnBoundary() {
        // Given
        TimeWindow timeWindow = durationWindow(Duration.ofHours(6), null);

        // When
        Pair<ZonedDateTime, ZonedDateTime> boundaries = timeWindow.boundaries(utc(15, 12, 0));

        // Then
        assertThat(boundaries.getLeft()).isEqualTo(utc(15, 12, 0));
    }

    @Test
    void shouldStepBackSixHoursWindowWhenShiftedStartIsInTheFuture() {
        // Given: windows start at 03:00, 09:00, 15:00 and 21:00
        TimeWindow timeWindow = durationWindow(Duration.ofHours(6), Duration.ofHours(3));

        // When
        Pair<ZonedDateTime, ZonedDateTime> afterMidnight = timeWindow.boundaries(utc(15, 1, 0));
        Pair<ZonedDateTime, ZonedDateTime> beforeNine = timeWindow.boundaries(utc(15, 8, 0));

        // Then
        assertThat(afterMidnight.getLeft()).isEqualTo(utc(14, 21, 0));
        assertThat(beforeNine.getLeft()).isEqualTo(utc(15, 3, 0));
    }
}
