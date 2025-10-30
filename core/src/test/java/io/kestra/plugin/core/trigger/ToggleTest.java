package io.kestra.plugin.core.trigger;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

@KestraTest(startRunner = true, startScheduler = true)
class ToggleTest {
    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    private AbstractScheduler scheduler;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/trigger-toggle.yaml"})
    void toggle() throws Exception {
        // we need to await for the scheduler to be ready otherwise there may be an issue with updating the trigger
        Await.until(() -> scheduler.isReady(), Duration.ofMillis(100), Duration.ofSeconds(20));

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

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger", "trigger-toggle");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);

        countDownLatch.await(10, TimeUnit.SECONDS);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        Trigger lastTrigger = receive.blockLast();
        assertThat(lastTrigger).isNotNull();
        assertThat(lastTrigger.getDisabled()).isFalse();
    }
}