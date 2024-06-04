package io.kestra.plugin.core.trigger;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux<Trigger> receive = TestsUtils.receive(triggerQueue, either -> {
            if (either.isLeft()) {
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests.trigger", "trigger-toggle");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        countDownLatch.await(10, TimeUnit.SECONDS);
        assertThat(countDownLatch.getCount(), is(0L));
        Trigger lastTrigger = receive.blockLast();
        assertThat(lastTrigger, notNullValue());
        assertThat(lastTrigger.getDisabled(), is(false));
    }
}