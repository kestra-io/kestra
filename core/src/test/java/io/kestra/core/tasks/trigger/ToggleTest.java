package io.kestra.core.tasks.trigger;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ToggleTest extends AbstractMemoryRunnerTest {
    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

    @Test
    void toggle() throws Exception {
        Trigger trigger = Trigger
            .builder()
            .triggerId("schedule")
            .flowId("trigger-toggle")
            .namespace("io.kestra.tests.trigger")
            .date(ZonedDateTime.now())
            .disabled(true)
            .build();
        triggerRepository.save(trigger);

        AtomicReference<Trigger> triggerRef = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        triggerQueue.receive(either -> {
            if (either.isLeft()) {
                triggerRef.set(either.getLeft());
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-toggle");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        countDownLatch.await(10, TimeUnit.SECONDS);
        assertThat(countDownLatch.getCount(), is(0L));
        assertThat(triggerRef.get(), notNullValue());
        assertThat(triggerRef.get().getDisabled(), is(false));
    }
}