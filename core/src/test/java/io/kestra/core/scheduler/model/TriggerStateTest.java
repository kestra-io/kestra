package io.kestra.core.scheduler.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

    @Test
    void shouldClearTheBlockWhenDisabled() {
        TriggerState blocked = TriggerState.of(TRIGGER_ID, TriggerType.SCHEDULE, null, 0)
            .blockedBy(clockAt(NOW), new Blocker(Blocker.Reason.POLICY, List.of("deny-log"), "denied"));

        assertThat(blocked.disabled(clockAt(NOW), true).getBlockedBy()).isNull();
    }

    @Test
    void shouldClearTheBlockWhenReset() {
        TriggerState blocked = TriggerState.of(TRIGGER_ID, TriggerType.SCHEDULE, null, 0)
            .blockedBy(clockAt(NOW), new Blocker(Blocker.Reason.POLICY, List.of("deny-log"), "denied"));

        assertThat(blocked.reset(clockAt(NOW)).getBlockedBy()).isNull();
    }

    @Test
    void shouldNotExposeTheCauseOfAFailedPolicyCheck() {
        Blocker blocker = Blocker.from(new FlowBlockedException(Blocker.Reason.POLICY_CHECK_FAILED, "Governance policy resolution failed: connection reset", List.of(), new RuntimeException()));

        assertThat(blocker.reason()).isEqualTo(Blocker.Reason.POLICY_CHECK_FAILED);
        assertThat(blocker.message()).doesNotContain("connection reset");
    }

    @Test
    void shouldStayDisabledWhenExecutionTerminatesInAStateNotInStopAfter() {
        TriggerState disabled = TriggerState.of(TRIGGER_ID, TriggerType.SCHEDULE, List.of(State.Type.FAILED), 0)
            .disabled(clockAt(NOW), true);

        assertThat(disabled.updateOnExecutionTerminated(clockAt(NOW), State.Type.SUCCESS).isDisabled()).isTrue();
    }

    @Test
    void shouldStayDisabledWhenExecutionIsCreatedInAStateNotInStopAfter() {
        TriggerState disabled = TriggerState.of(TRIGGER_ID, TriggerType.POLLING, List.of(State.Type.FAILED), 0)
            .disabled(clockAt(NOW), true);

        assertThat(disabled.updateOnExecutionCreated(clockAt(NOW), State.Type.CREATED).isDisabled()).isTrue();
    }

    @Test
    void shouldDisableWhenExecutionTerminatesInAStopAfterState() {
        TriggerState enabled = TriggerState.of(TRIGGER_ID, TriggerType.SCHEDULE, List.of(State.Type.FAILED), 0);

        assertThat(enabled.updateOnExecutionTerminated(clockAt(NOW), State.Type.FAILED).isDisabled()).isTrue();
        assertThat(enabled.updateOnExecutionTerminated(clockAt(NOW), State.Type.SUCCESS).isDisabled()).isFalse();
    }

    @Test
    void shouldNotReportDefinitionChangeWhenPluginUpgradeAddsDefaultedProperty() {
        TriggerState computedBeforeUpgrade = TriggerState.of(TRIGGER_ID, TriggerV1.builder().id("trigger").type("io.kestra.tests.Trigger").build(), 0);

        boolean changed = computedBeforeUpgrade.hasDefinitionChanged(TriggerV2.builder().id("trigger").type("io.kestra.tests.Trigger").build());

        assertThat(changed).isFalse();
    }

    @Test
    void shouldReportDefinitionChangeWhenDefaultedPropertyIsOverridden() {
        TriggerState computed = TriggerState.of(TRIGGER_ID, TriggerV2.builder().id("trigger").type("io.kestra.tests.Trigger").build(), 0);

        boolean changed = computed.hasDefinitionChanged(TriggerV2.builder().id("trigger").type("io.kestra.tests.Trigger").batchSize(50).build());

        assertThat(changed).isTrue();
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class TriggerV1 extends AbstractTrigger {
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class TriggerV2 extends AbstractTrigger {
        @Builder.Default
        private int batchSize = 10;
    }
}
