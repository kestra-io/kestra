package io.kestra.core.scheduler.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.triggers.TriggerId;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerStateTest {

    private static final TriggerId TRIGGER_ID = TriggerId.of("main", "io.kestra.tests", "flow", "trigger");
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void shouldNotReuseDispatchEpochWhenTriggerIsRecreated() {
        TriggerState deleted = TriggerState.of(TRIGGER_ID, TriggerType.REALTIME, null, 0)
            .nextDispatchEpoch(clockAt(NOW));

        TriggerState recreated = TriggerState.of(TRIGGER_ID, TriggerType.REALTIME, null, 0)
            .nextDispatchEpoch(clockAt(NOW.plus(Duration.ofSeconds(1))));

        assertThat(recreated.getDispatchEpoch()).isGreaterThan(deleted.getDispatchEpoch());
    }

    @Test
    void shouldIncreaseDispatchEpochWhenClockDoesNotAdvance() {
        Clock clock = clockAt(NOW);
        TriggerState first = TriggerState.of(TRIGGER_ID, TriggerType.REALTIME, null, 0).nextDispatchEpoch(clock);

        TriggerState second = first.nextDispatchEpoch(clock);

        assertThat(second.getDispatchEpoch()).isGreaterThan(first.getDispatchEpoch());
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
