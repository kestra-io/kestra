package io.kestra.core.models.tasks.retrys;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void shouldReturnIntervalDelayForFirstRetry() {
        // Given
        var retry = Constant.builder()
            .interval(Duration.ofSeconds(5))
            .build();

        // When
        Instant next = retry.nextRetryDate(1, BASE);

        // Then
        assertThat(Duration.between(BASE, next)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnSameDelayRegardlessOfAttemptCount() {
        // Given
        var retry = Constant.builder()
            .interval(Duration.ofSeconds(10))
            .build();

        // When / Then – delay is always interval, independent of attempt number
        assertThat(Duration.between(BASE, retry.nextRetryDate(1, BASE))).isEqualTo(Duration.ofSeconds(10));
        assertThat(Duration.between(BASE, retry.nextRetryDate(2, BASE))).isEqualTo(Duration.ofSeconds(10));
        assertThat(Duration.between(BASE, retry.nextRetryDate(5, BASE))).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldAddIntervalToLastAttemptTimestamp() {
        // Given
        var retry = Constant.builder()
            .interval(Duration.ofMinutes(2))
            .build();
        Instant lastAttempt = Instant.parse("2024-06-15T12:30:00Z");

        // When
        Instant next = retry.nextRetryDate(3, lastAttempt);

        // Then
        assertThat(next).isEqualTo(Instant.parse("2024-06-15T12:32:00Z"));
    }
}
