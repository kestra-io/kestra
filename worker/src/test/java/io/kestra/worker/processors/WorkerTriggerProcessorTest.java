package io.kestra.worker.processors;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@KestraTest
class WorkerTriggerProcessorTest {
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private WorkerSecurityService workerSecurityService;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private ExecutionKilledManager executionKilledManager;

    @Inject
    private TracerFactory tracerFactory;

    @AfterAll
    static void releaseBlockedEvaluations() {
        BlockingTrigger.RELEASE.countDown();
    }

    @Test
    void shouldReportTriggerResultWhenEvaluationIsAbandoned() throws Exception {
        WorkerTrigger workerTrigger = workerTrigger(
            BlockingTrigger.builder()
                .id("blocking")
                .type(BlockingTrigger.class.getName())
                .failOnTriggerError(true)
                .build()
        );
        WorkerQueue<WorkerTriggerResult> results = new InMemoryWorkerQueue<>(10);
        WorkerTriggerProcessor processor = processor(results);

        // Given an evaluation that has started and answers neither a return nor an interrupt
        Future<?> processing = submit(() -> processor.process(workerTrigger));
        assertThat(BlockingTrigger.STARTED.await(10, TimeUnit.SECONDS)).isTrue();

        // When the worker gives up waiting for it
        processor.onTimeout(workerTrigger);

        // Then the trigger is reported, so the scheduler releases its evaluation lock, although
        // the evaluation itself is still running
        WorkerTriggerResult result = results.poll(Duration.ofSeconds(5));
        assertThat(result).isNotNull();
        assertThat(result.evaluation()).isNotNull();
        assertThat(result.evaluation().stateType()).isEqualTo(State.Type.FAILED);
        assertThat(processing.isDone()).isFalse();

        // And the outcome the plugin produces afterwards is dropped rather than reported a second time
        BlockingTrigger.RELEASE.countDown();
        processing.get(10, TimeUnit.SECONDS);
        assertThat(results.poll(Duration.ofMillis(500))).isNull();
    }

    @Test
    void shouldReportTriggerResultWhenTimeoutFiresBeforeTheEvaluationStarted() throws Exception {
        // Given a job whose deadline passes while it is still queued, so it never built a condition context
        WorkerTrigger workerTrigger = workerTrigger(
            SleepTrigger.builder().id("sleep").type(SleepTrigger.class.getName()).duration(1L).build()
        );
        WorkerQueue<WorkerTriggerResult> results = new InMemoryWorkerQueue<>(10);

        // When the worker gives up on it without process() ever having run
        processor(results).onTimeout(workerTrigger);

        // Then a terminal result is still sent, or the trigger stays locked on the scheduler
        WorkerTriggerResult result = results.poll(Duration.ofSeconds(5));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldNotStartTheEvaluationWhenTheDeadlinePassedBeforeItBegan() throws Exception {
        // Given a deadline that lands before the evaluation is published to the processor
        WorkerTrigger workerTrigger = workerTrigger(
            RecordingTrigger.builder()
                .id("recording")
                .type(RecordingTrigger.class.getName())
                .failOnTriggerError(true)
                .build()
        );
        WorkerTriggerProcessor processor = processor(new InMemoryWorkerQueue<>(10));
        processor.onTimeout(workerTrigger);

        // When the job finally runs
        submit(() -> processor.process(workerTrigger)).get(10, TimeUnit.SECONDS);

        // Then it does not start an evaluation nobody is waiting for any more
        assertThat(RecordingTrigger.EVALUATED.get()).isFalse();
    }

    @Test
    void shouldBoundPollingTriggersOnlyWhenComputingTheTimeout() {
        WorkerTriggerProcessor processor = processor(new InMemoryWorkerQueue<>(10));
        WorkerTrigger polling = workerTrigger(
            SleepTrigger.builder().id("sleep").type(SleepTrigger.class.getName()).duration(1L).build()
        );
        WorkerTrigger realtime = mock(WorkerTrigger.class);
        when(realtime.getTrigger())
            .thenReturn(mock(AbstractTrigger.class, withSettings().extraInterfaces(RealtimeTriggerInterface.class)));

        assertThat(processor.timeout(polling)).isEqualTo(TIMEOUT);
        // Realtime triggers run for their whole lifetime, so a deadline would kill every one of them.
        assertThat(processor.timeout(realtime)).isNull();
    }

    @Test
    void shouldEmitTriggerResultWhenEvaluationCompletesInTime() throws Exception {
        WorkerTrigger workerTrigger = workerTrigger(
            SleepTrigger.builder().id("sleep").type(SleepTrigger.class.getName()).duration(1L).build()
        );
        WorkerQueue<WorkerTriggerResult> results = new InMemoryWorkerQueue<>(10);

        submit(() -> processor(results).process(workerTrigger)).get(10, TimeUnit.SECONDS);

        WorkerTriggerResult result = results.poll(Duration.ofSeconds(1));
        assertThat(result).isNotNull();
        assertThat(result.evaluation()).isNull();
    }

    /**
     * Processes on a separate daemon thread so that an evaluation left running never hangs the build.
     */
    private Future<?> submit(Runnable runnable) {
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "worker-trigger-processor-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(runnable);
        } finally {
            executor.shutdown();
        }
    }

    private WorkerTriggerProcessor processor(WorkerQueue<WorkerTriggerResult> results) {
        return new WorkerTriggerProcessor(
            WorkerGroups.DEFAULT_ID,
            metricRegistry,
            workerSecurityService,
            tracerFactory.getTracer(Worker.class, "WORKER"),
            runContextInitializer,
            new InMemoryWorkerQueue<>(10),
            results,
            executionKilledManager,
            TIMEOUT
        );
    }

    private WorkerTrigger workerTrigger(AbstractTrigger trigger) {
        return WorkerTrigger.builder()
            .trigger(trigger)
            .data(new WorkerTriggerData(
                "tenant", "io.kestra.tests", "flow", ZonedDateTime.now(), null, null, null,
                Collections.emptyMap(), null, Collections.emptyMap()
            ))
            .build();
    }

    @SuperBuilder
    @NoArgsConstructor
    public static class RecordingTrigger extends AbstractTrigger implements PollingTriggerInterface {
        static final AtomicBoolean EVALUATED = new AtomicBoolean(false);

        @Override
        public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
            EVALUATED.set(true);
            return Optional.empty();
        }

        @Override
        public Duration getInterval() {
            return Duration.ofSeconds(1);
        }
    }

    @SuperBuilder
    @NoArgsConstructor
    public static class BlockingTrigger extends AbstractTrigger implements PollingTriggerInterface {
        // Static so that they stay invisible to the trigger's serialization during run-context
        // initialization, which caps this trigger at one evaluation per JVM.
        static final CountDownLatch STARTED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Override
        public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
            STARTED.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    RELEASE.await();
                    break;
                } catch (InterruptedException e) {
                    // Swallowed on purpose: this reproduces a plugin whose blocking call does not answer an interrupt.
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }

        @Override
        public Duration getInterval() {
            return Duration.ofSeconds(1);
        }
    }
}
