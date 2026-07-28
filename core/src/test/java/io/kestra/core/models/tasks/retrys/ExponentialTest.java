package io.kestra.core.models.tasks.retrys;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Exponential#nextRetryDate(Integer, Instant)}.
 *
 * <p>Verifies that the scheduler path matches Failsafe {@code withBackoff} semantics:
 * delay before retry {@code n} = {@code interval * factor^(n-1)}, clamped to {@code maxInterval}.
 */
class ExponentialTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void shouldReturnIntervalForFirstRetry() {
        // Given
        var retry = Exponential.builder()
            .interval(Duration.ofSeconds(5))
            .maxInterval(Duration.ofHours(1))
            .build();

        // When
        Instant next = retry.nextRetryDate(1, BASE);

        // Then – delay == interval (factor^0 == 1)
        assertThat(Duration.between(BASE, next)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldGrowExponentiallyWithDefaultFactor() {
        // Given – interval=1s, maxInterval=1h, factor=2 (default)
        var retry = Exponential.builder()
            .interval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofHours(1))
            .build();

        // When / Then – 1s, 2s, 4s, 8s (= interval * 2^(n-1))
        assertThat(Duration.between(BASE, retry.nextRetryDate(1, BASE))).isEqualTo(Duration.ofSeconds(1));
        assertThat(Duration.between(BASE, retry.nextRetryDate(2, BASE))).isEqualTo(Duration.ofSeconds(2));
        assertThat(Duration.between(BASE, retry.nextRetryDate(3, BASE))).isEqualTo(Duration.ofSeconds(4));
        assertThat(Duration.between(BASE, retry.nextRetryDate(4, BASE))).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void shouldGrowExponentiallyWithCustomFactor() {
        // Given – interval=1s, maxInterval=1h, factor=3
        var retry = Exponential.builder()
            .interval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofHours(1))
            .delayFactor(3d)
            .build();

        // When / Then – 1s, 3s, 9s (= interval * 3^(n-1))
        assertThat(Duration.between(BASE, retry.nextRetryDate(1, BASE))).isEqualTo(Duration.ofSeconds(1));
        assertThat(Duration.between(BASE, retry.nextRetryDate(2, BASE))).isEqualTo(Duration.ofSeconds(3));
        assertThat(Duration.between(BASE, retry.nextRetryDate(3, BASE))).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void shouldClampToMaxInterval() {
        // Given – interval=5s, maxInterval=10s, factor=2 (default)
        var retry = Exponential.builder()
            .interval(Duration.ofSeconds(5))
            .maxInterval(Duration.ofSeconds(10))
            .build();

        // When / Then – attempt 1: 5s (5*2^0), attempt 2: 10s (5*2^1 = maxInterval), attempt 3: capped at 10s (would be 20s)
        assertThat(Duration.between(BASE, retry.nextRetryDate(1, BASE))).isEqualTo(Duration.ofSeconds(5));
        assertThat(Duration.between(BASE, retry.nextRetryDate(2, BASE))).isEqualTo(Duration.ofSeconds(10));
        assertThat(Duration.between(BASE, retry.nextRetryDate(3, BASE))).isEqualTo(Duration.ofSeconds(10));
    }
}
