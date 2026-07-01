package io.kestra.core.models.tasks.retrys;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RandomTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void shouldReturnDelayWithinConfiguredBounds() {
        // Given
        var retry = Random.builder()
            .minInterval(Duration.ofSeconds(5))
            .maxInterval(Duration.ofSeconds(15))
            .build();

        // When
        Instant next = retry.nextRetryDate(1, BASE);

        // Then
        Duration delay = Duration.between(BASE, next);
        assertThat(delay).isGreaterThanOrEqualTo(Duration.ofSeconds(5));
        assertThat(delay).isLessThan(Duration.ofSeconds(15));
    }

    @RepeatedTest(20)
    void shouldAlwaysStayWithinBoundsAcrossMultipleInvocations() {
        // Given
        var retry = Random.builder()
            .minInterval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofSeconds(10))
            .build();

        // When
        Instant next = retry.nextRetryDate(1, BASE);

        // Then
        Duration delay = Duration.between(BASE, next);
        assertThat(delay).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        assertThat(delay).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void shouldReturnDelayWithinBoundsRegardlessOfAttemptCount() {
        // Given
        var retry = Random.builder()
            .minInterval(Duration.ofSeconds(2))
            .maxInterval(Duration.ofSeconds(8))
            .build();

        // When / Then – attemptCount has no effect on bounds (delay is always random within range)
        for (int attempt = 1; attempt <= 5; attempt++) {
            Duration delay = Duration.between(BASE, retry.nextRetryDate(attempt, BASE));
            assertThat(delay).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
            assertThat(delay).isLessThan(Duration.ofSeconds(8));
        }
    }

    @Test
    void shouldAddDelayToLastAttemptTimestamp() {
        // Given
        var retry = Random.builder()
            .minInterval(Duration.ofSeconds(3))
            .maxInterval(Duration.ofSeconds(7))
            .build();
        Instant lastAttempt = Instant.parse("2024-06-15T12:00:00Z");

        // When
        Instant next = retry.nextRetryDate(1, lastAttempt);

        // Then – result is always after lastAttempt + minInterval and before lastAttempt + maxInterval
        assertThat(next).isAfterOrEqualTo(lastAttempt.plusSeconds(3));
        assertThat(next).isBefore(lastAttempt.plusSeconds(7));
    }
}
