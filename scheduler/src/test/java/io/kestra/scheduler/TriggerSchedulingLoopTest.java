package io.kestra.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.utils.Await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.noop.NoopTimer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TriggerSchedulingLoopTest {

    private TriggerScheduler triggerScheduler;
    private TriggerEventHandler triggerEventHandler;
    private Clock clock;

    @BeforeEach
    void setUp() {
        triggerScheduler = mock(TriggerScheduler.class);
        triggerEventHandler = mock(TriggerEventHandler.class);
        clock = Clock.systemUTC();
    }

    TriggerSchedulingLoop createLoop() {
        MetricRegistry mkMetricRegistry = mock(MetricRegistry.class);
        Mockito.when(mkMetricRegistry.timer(anyString(), anyString(), anyString(), anyString())).thenReturn(new NoopTimer(mock(Meter.Id.class)));
        Mockito.when(mkMetricRegistry.counter(anyString(), anyString(), anyString(), anyString())).thenReturn(mock(Counter.class));
        return new TriggerSchedulingLoop(1, triggerScheduler, triggerEventHandler, mkMetricRegistry, clock);
    }

    @Test
    void shouldSetAssignmentsWhenSetAssignmentsGivenNewAssignments() {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();

        // WHEN
        loop.setAssignments(Set.of(1, 2, 3));

        // THEN
        assertThat(loop.assignments()).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void shouldPauseAndResumeLoopGivenPausedState() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();

        // WHEN
        Thread.sleep(2000); // wait for at-least one iteration
        loop.pause();
        verify(triggerScheduler, times(1)).onStart(any(), any(), eq(Set.of(1)));
        verify(triggerScheduler, atLeast(2)).onSchedule(any(), any(), eq(Set.of(1)));
        Thread.sleep(500); // let any in-flight iteration complete after pause
        Mockito.clearInvocations(triggerScheduler);
        Thread.sleep(2000);

        // THEN
        verifyNoMoreInteractions(triggerScheduler);

        loop.resume();
        loop.stop();
        thread.join();

        // THEN
        assertThat(loop.assignments()).contains(1);
    }

    @Test
    void shouldRunOnScheduleWhenLoopTickGivenAssignments() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();
        Thread.sleep(50); // let loop start and call onStart/onSchedule

        // WHEN
        loop.stop();
        thread.join();

        // THEN
        verify(triggerScheduler, atLeastOnce()).onStart(any(), any(), eq(Set.of(1)));
        verify(triggerScheduler, atLeastOnce()).onSchedule(any(), any(), eq(Set.of(1)));
    }

    @Test
    void shouldWarnOnEveryEvaluationButTheColdOneWhenEvaluationOverrunsTheInterval() throws Exception {
        // GIVEN
        Logger logger = (Logger) LoggerFactory.getLogger(TriggerSchedulingLoop.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        AtomicInteger evaluations = new AtomicInteger();
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        // Every evaluation overruns the interval, so a clean first cycle can only come from the skip.
        Mockito.when(triggerScheduler.onSchedule(any(), any(), any())).thenAnswer(invocation ->
        {
            Thread.sleep(1_300);
            return evaluations.incrementAndGet();
        });

        Thread thread = new Thread(loop);
        thread.start();

        try {
            // WHEN
            Await.until(() -> evaluations.get() >= 2, Duration.ofMillis(10), Duration.ofSeconds(30));
        } finally {
            loop.stop();
            thread.join();
            logger.detachAppender(appender);
        }

        // THEN
        long warnings = appender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("cannot keep up with its trigger load"))
            .count();
        assertThat(warnings).isEqualTo(evaluations.get() - 1);
    }

    @Test
    void shouldKeepScheduleGridWhenPreviousIterationRanLate() throws Exception {
        // GIVEN
        AdjustableClock adjustableClock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        clock = adjustableClock;
        AtomicInteger evaluations = new AtomicInteger();
        TriggerSchedulingLoop loop = createLoop();
        Mockito.when(triggerScheduler.onSchedule(any(), any(), any())).thenAnswer(invocation -> evaluations.incrementAndGet());

        Thread thread = new Thread(loop);
        thread.start();

        try {
            // WHEN
            awaitFirstIteration(loop);
            adjustableClock.advance(Duration.ofMillis(5_400));
            loop.setAssignments(Set.of(1));
            Await.until(() -> evaluations.get() == 1, Duration.ofMillis(10), Duration.ofSeconds(10));

            // THEN
            adjustableClock.advance(Duration.ofMillis(600));
            Await.until(() -> evaluations.get() == 2, Duration.ofMillis(10), Duration.ofSeconds(10));
        } finally {
            loop.stop();
            thread.join();
        }
    }

    @Test
    void shouldNotEvaluateTwiceWhenWakingExactlyOneIntervalLate() throws Exception {
        // GIVEN
        AdjustableClock adjustableClock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"));
        clock = adjustableClock;
        AtomicInteger evaluations = new AtomicInteger();
        TriggerSchedulingLoop loop = createLoop();
        Mockito.when(triggerScheduler.onSchedule(any(), any(), any())).thenAnswer(invocation -> evaluations.incrementAndGet());

        Thread thread = new Thread(loop);
        thread.start();

        try {
            // WHEN
            awaitFirstIteration(loop);
            loop.setAssignments(Set.of(1));
            Await.until(() -> evaluations.get() >= 1, Duration.ofMillis(10), Duration.ofSeconds(10));
            // The slot is now one interval ahead, so the next iteration lands exactly on it plus one interval.
            adjustableClock.advance(Duration.ofSeconds(2));

            // THEN
            Await.until(() -> evaluations.get() >= 2, Duration.ofMillis(10), Duration.ofSeconds(10));
            Thread.sleep(200); // leave room for any catch-up iteration to fire
            assertThat(evaluations.get()).isEqualTo(2);
        } finally {
            loop.stop();
            thread.join();
        }
    }

    @Test
    void shouldStopLoopGracefullyGivenRunningLoop() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        Thread thread = new Thread(loop);
        thread.start();
        Thread.sleep(50); // let loop start

        // WHEN
        loop.stop();
        thread.join();

        // THEN
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    void shouldNotStartSchedulingWhenStoppedBeforeTheSubmissionRuns() throws InterruptedException {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));

        // WHEN
        loop.stop();
        Thread thread = new Thread(loop);
        thread.start();
        thread.join(2000);

        // THEN
        assertThat(loop.isRunning()).isFalse();
        assertThat(thread.isAlive()).isFalse();
        verifyNoInteractions(triggerScheduler);
    }

    @Test
    void shouldProcessAllEventsInStrictOrderAsync() throws Exception {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        loop.setAssignments(Set.of(1));
        TriggerEvent event1 = mock(TriggerEvent.class);
        TriggerEvent event2 = mock(TriggerEvent.class);

        Thread thread = new Thread(loop);
        thread.start(); // start the event-loop
        try {
            // WHEN
            CompletableFuture<Void> allFutures = loop.addTriggerEvents(1, List.of(event1, event2));
            allFutures.join(); // wait until all events added

            // THEN
            verify(triggerEventHandler, times(1)).handle(any(), eq(1), eq(event1));
            verify(triggerEventHandler, times(1)).handle(any(), eq(1), eq(event2));
        } finally {
            // cleanup
            loop.stop();
            thread.join();
        }
    }

    @Test
    void shouldProcessAllEventsInStrictOrder() {
        // GIVEN
        TriggerSchedulingLoop loop = createLoop();
        TriggerEvent event1 = mock(TriggerEvent.class);
        TriggerEvent event2 = mock(TriggerEvent.class);

        // WHEN - with 2 events
        loop.addTriggerEvents(1, List.of(event1, event2));
        int processed = loop.processTriggerEvents();

        // THEN
        assertThat(processed).isEqualTo(2);

        // WHEN - with 0 event
        processed = loop.processTriggerEvents();

        // THEN
        assertThat(processed).isEqualTo(0);
    }

    /**
     * Waits for the loop to complete one iteration, so that it has read its initial schedule time
     * before the test moves the clock.
     */
    private static void awaitFirstIteration(TriggerSchedulingLoop loop) throws Exception {
        loop.doOnEndLoop(() ->
        {
        }).get(10, TimeUnit.SECONDS);
    }

    private static final class AdjustableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private AdjustableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}