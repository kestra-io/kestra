package io.kestra.core.reporter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class SchedulesTest {
    
    @Test
    void shouldTriggerAfterPeriodGivenEnoughTimeHasPassed() {
        // Given
        var schedule = Schedules.every(Duration.ofHours(1));
        Instant now = Instant.now();
        
        // When
        boolean firstRun = schedule.shouldRun(now);
        boolean fiveMinutesLater = schedule.shouldRun(now.plus(Duration.ofMinutes(5)));
        boolean oneHourLater = schedule.shouldRun(now.plus(Duration.ofHours(1)));
        
        // Then
        assertThat(firstRun).isTrue();
        assertThat(fiveMinutesLater).isFalse();
        assertThat(oneHourLater).isTrue();
    }
    
    @Test
    void shouldNotTriggerGivenPeriodHasNotElapsed() {
        // Given
        var schedule = Schedules.every(Duration.ofMinutes(30));
        Instant now = Instant.now();
        
        // When
        boolean firstRun = schedule.shouldRun(now);
        boolean almost30Minutes = schedule.shouldRun(now.plus(Duration.ofMinutes(29)));
        
        // Then
        assertThat(firstRun).isTrue();
        assertThat(almost30Minutes).isFalse();
    }
    
    @Test
    void shouldTriggerHourlyGivenOneHourHasElapsed() {
        // Given
        var schedule = Schedules.hourly();
        Instant now = Instant.now();
        
        // When
        boolean firstRun = schedule.shouldRun(now);
        boolean nextHour = schedule.shouldRun(now.plus(Duration.ofHours(1)));
        
        // Then
        assertThat(firstRun).isTrue();
        assertThat(nextHour).isTrue();
    }
    
    @Test
    void shouldTriggerDailyGivenOneDayHasElapsed() {
        // Given
        var schedule = Schedules.daily();
        Instant now = Instant.now();
        
        // When
        boolean firstRun = schedule.shouldRun(now);
        boolean nextDay = schedule.shouldRun(now.plus(Duration.ofDays(1)));
        
        // Then
        assertThat(firstRun).isTrue();
        assertThat(nextDay).isTrue();
    }
    
    @Test
    void shouldThrowExceptionGivenZeroOrNegativeDuration() {
        // Given / When / Then
        assertThatThrownBy(() -> Schedules.every(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Period must be positive");
        
        assertThatThrownBy(() -> Schedules.every(Duration.ofSeconds(-5)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Period must be positive");
    }
}