package io.kestra.scheduler;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class SchedulerTriggerStateInterfaceTest {
    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .date(ZonedDateTime.now());
    }

    @Test
    void all() {
        Trigger.TriggerBuilder<?, ?> builder = trigger();

        Optional<Trigger> find = triggerState.findLast(builder.build());
        assertThat(find.isPresent()).isFalse();

        Trigger save = triggerState.update(builder.build());

        find = triggerState.findLast(save);

        assertThat(find.isPresent()).isTrue();
        assertThat(find.get().getExecutionId()).isEqualTo(save.getExecutionId());

        save = triggerState.update(builder.executionId(IdUtils.create()).build());

        find = triggerState.findLast(save);

        assertThat(find.isPresent()).isTrue();
        assertThat(find.get().getExecutionId()).isEqualTo(save.getExecutionId());
    }
}
