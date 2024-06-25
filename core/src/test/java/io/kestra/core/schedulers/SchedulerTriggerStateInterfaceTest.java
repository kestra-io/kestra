package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
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
        assertThat(find.isPresent(), is(false));

        Trigger save = triggerState.update(builder.build());

        find = triggerState.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));

        save = triggerState.update(builder.executionId(IdUtils.create()).build());

        find = triggerState.findLast(save);

        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecutionId(), is(save.getExecutionId()));
    }
}
