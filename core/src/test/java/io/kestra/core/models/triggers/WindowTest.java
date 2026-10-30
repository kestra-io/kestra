package io.kestra.core.models.triggers;

import java.time.Duration;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowTest {
    private static final String TIMEZONE = "Asia/Tokyo";

    @Test
    void shouldPropagateTimezoneForDailyTimeDeadline() {
        // Given
        Window window = Window.builder().deadline(LocalTime.of(9, 0)).timezone(TIMEZONE).build();

        // When
        TimeWindow timeWindow = window.toTimeWindow();

        // Then
        assertThat(timeWindow.getType()).isEqualTo(TimeWindow.Type.DAILY_TIME_DEADLINE);
        assertThat(timeWindow.getTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    void shouldPropagateTimezoneForDailyTimeWindow() {
        // Given
        Window window = Window.builder().from(LocalTime.of(6, 0)).to(LocalTime.of(9, 0)).timezone(TIMEZONE).build();

        // When
        TimeWindow timeWindow = window.toTimeWindow();

        // Then
        assertThat(timeWindow.getType()).isEqualTo(TimeWindow.Type.DAILY_TIME_WINDOW);
        assertThat(timeWindow.getTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    void shouldPropagateTimezoneForSlidingWindow() {
        // Given
        Window window = Window.builder().lookback(Duration.ofHours(1)).timezone(TIMEZONE).build();

        // When
        TimeWindow timeWindow = window.toTimeWindow();

        // Then
        assertThat(timeWindow.getType()).isEqualTo(TimeWindow.Type.SLIDING_WINDOW);
        assertThat(timeWindow.getTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    void shouldPropagateTimezoneForDurationWindow() {
        // Given
        Window window = Window.builder().every(Duration.ofDays(1)).offset(Duration.ofHours(6)).timezone(TIMEZONE).build();

        // When
        TimeWindow timeWindow = window.toTimeWindow();

        // Then
        assertThat(timeWindow.getType()).isEqualTo(TimeWindow.Type.DURATION_WINDOW);
        assertThat(timeWindow.getTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    void shouldPropagateNullTimezoneWhenNotSet() {
        // Given
        Window window = Window.builder().build();

        // When
        TimeWindow timeWindow = window.toTimeWindow();

        // Then
        assertThat(timeWindow.getTimezone()).isNull();
    }
}
