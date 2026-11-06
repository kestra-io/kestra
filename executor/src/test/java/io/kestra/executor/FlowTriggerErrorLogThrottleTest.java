package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.kestra.executor.testkit.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTriggerErrorLogThrottleTest {
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final FlowTriggerErrorLogThrottle throttle = new FlowTriggerErrorLogThrottle(WINDOW, clock);

    @Test
    void logsTheFirstOccurrenceThenSuppressesTheRestOfTheWindow() {
        assertThat(throttle.shouldLog("flow_trigger")).isTrue();

        clock.advance(WINDOW.minusSeconds(1));
        assertThat(throttle.shouldLog("flow_trigger")).isFalse();
    }

    @Test
    void logsAgainOnceTheWindowHasElapsed() {
        assertThat(throttle.shouldLog("flow_trigger")).isTrue();

        clock.advance(WINDOW);
        assertThat(throttle.shouldLog("flow_trigger")).isTrue();
        assertThat(throttle.shouldLog("flow_trigger")).isFalse();
    }

    @Test
    void throttlesEachKeyIndependently() {
        assertThat(throttle.shouldLog("flow_trigger")).isTrue();

        assertThat(throttle.shouldLog("flow_other-trigger")).isTrue();
        assertThat(throttle.shouldLog("flow_trigger")).isFalse();
    }
}
