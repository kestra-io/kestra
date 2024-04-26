package io.kestra.core.schedulers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SchedulerTriggerChangeTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<Flow> flowQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logsQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killedQueue;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    public static Flow createFlow(Duration sleep) {
        SleepTriggerTest schedule = SleepTriggerTest.builder()
            .id("sleep")
            .type(SleepTriggerTest.class.getName())
            .sleep(sleep)
            .build();

        return Flow.builder()
            .id(SchedulerTriggerChangeTest.class.getSimpleName())
            .namespace("io.kestra.unittest")
            .revision(1)
            .triggers(List.of(schedule))
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("{{ inputs.testInputs }}")
                .build())
            )
            .build();
    }

    @Test
    void run() throws Exception {
        CountDownLatch executionQueueCount = new CountDownLatch(1);
        CountDownLatch executionKilledCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();
        AtomicReference<ExecutionKilledTrigger> lastKilled = new AtomicReference<>();

        // wait for execution
        Runnable executionQueueStop = executionQueue.receive(SchedulerThreadTest.class, either -> {
            lastExecution.set(either.getLeft());
            executionQueueCount.countDown();
        });

        // wait for killed
        Runnable killedQueueStop = killedQueue.receive(SchedulerThreadTest.class, either -> {
            lastKilled.set((ExecutionKilledTrigger) either.getLeft());
            executionKilledCount.countDown();
        });

        // wait for execution
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Runnable workerTriggerQueueStop = logsQueue.receive(either -> logs.add(either.getLeft()));

        // scheduler
        try (
            AbstractScheduler scheduler = new DefaultScheduler(
                applicationContext,
                flowListenersService,
                triggerState
            );
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            // start the worker as it execute polling triggers
            worker.run();
            scheduler.run();

            // emit a flow trigger to be started
            Flow flow = createFlow(Duration.ofSeconds(10));
            flowQueue.emit(flow);

            Await.until(() -> STARTED_COUNT == 1, Duration.ofMillis(100), Duration.ofSeconds(30));

            // the trigger available on the thread running
            WorkerTrigger workerTrigger = worker.getWorkerThreadTasks()
                .stream()
                .filter(workerJob -> workerJob instanceof WorkerTrigger)
                .map(workerJob -> (WorkerTrigger) workerJob)
                .findFirst()
                .orElseThrow();

            assertThat(((SleepTriggerTest) workerTrigger.getTrigger()).getSleep(), is(Duration.ofSeconds(10)));

            // emit an updated one that you kill the previous one
            flow = createFlow(Duration.ofMillis(1));
            flowQueue.emit(flow);

            // wait for the killed
            executionKilledCount.await(1, TimeUnit.MINUTES);
            assertThat(lastKilled.get().getTriggerId(), is("sleep"));

            // the trigger is restarted
            Await.until(() -> STARTED_COUNT == 2, Duration.ofMillis(100), Duration.ofSeconds(30));

            // the new trigger create an execution
            boolean sawSuccessExecution = executionQueueCount.await(1, TimeUnit.MINUTES);
            assertThat(sawSuccessExecution, is(true));
            assertThat(lastExecution.get().getTrigger().getVariables().get("sleep"), is(Duration.ofMillis(1).toString()));

            // log the sleep interrupted
            LogEntry matchingLog = TestsUtils.awaitLog(logs, log -> log.getMessage().contains("sleep interrupted"));
            assertThat(matchingLog.getTriggerId(), is("sleep"));

            executionQueueStop.run();
            workerTriggerQueueStop.run();
            killedQueueStop.run();;
        }
    }

    private static int STARTED_COUNT = 0;

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class SleepTriggerTest extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(2);
        private String format;
        private Duration sleep;

        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws InterruptedException {
            STARTED_COUNT++;

            Thread.sleep(sleep.toMillis());

            return Optional.of(TriggerService.generateExecution(this, conditionContext, context, Map.of("sleep", sleep.toString())));
        }
    }
}
