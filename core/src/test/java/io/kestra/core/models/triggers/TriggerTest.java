package io.kestra.core.models.triggers;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerTest {
    @Test
    void shouldKeepDisabledWhenFlowUpdatedWithUntouchedTrigger() throws Exception {
        Flow flow = Flow.builder().id("disable-check").namespace("test").build();
        Schedule schedule = Schedule.builder().id("every_2_hours").cron("0 */2 * * *").build();

        Trigger lastTrigger = Trigger.builder()
            .triggerId("every_2_hours")
            .disabled(true)
            .build();

        Trigger updated = Trigger.of(flow, schedule, ConditionContext.builder().build(), Optional.of(lastTrigger));

        assertThat(updated.getDisabled()).isTrue();
    }

    @Test
    void shouldKeepDisabledWhenFlowUpdatedWithChangedTriggerDefinition() throws Exception {
        Flow flow = Flow.builder().id("disable-check").namespace("test").build();
        Schedule schedule = Schedule.builder().id("every_2_hours").cron("0 */3 * * *").build();

        Trigger lastTrigger = Trigger.builder()
            .triggerId("every_2_hours")
            .disabled(true)
            .build();

        Trigger updated = Trigger.of(flow, schedule, ConditionContext.builder().build(), Optional.of(lastTrigger));

        assertThat(updated.getDisabled()).isTrue();
    }
}
