package io.kestra.core.server;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceLifecycleGuardTest {

    @Test
    void shouldStartThenStopWithoutWaiting() {
        // Given
        var guard = new ServiceLifecycleGuard();

        // When
        boolean canStart = guard.beginStart();
        boolean started = guard.endStart();
        long before = System.nanoTime();
        guard.beginStop(Duration.ofSeconds(10));
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        // Then
        assertThat(canStart).isTrue();
        assertThat(started).isTrue();
        assertThat(elapsedMs).isLessThan(1_000);
    }

    @Test
    void shouldRefuseStartWhenStopWasRequestedFirst() {
        // Given
        var guard = new ServiceLifecycleGuard();

        // When
        guard.beginStop(Duration.ofSeconds(10));

        // Then
        assertThat(guard.beginStart()).isFalse();
        assertThat(guard.isStopRequested()).isTrue();
    }

    @Test
    void shouldThrowOnDoubleStart() {
        // Given
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();

        // When/Then
        assertThatThrownBy(guard::beginStart).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBlockStopUntilStartupCompletes() throws InterruptedException {
        // Given: a startup in flight
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();

        var stopReturned = new CountDownLatch(1);
        Thread stopper = Thread.ofVirtual().start(() ->
        {
            guard.beginStop(Duration.ofSeconds(10));
            stopReturned.countDown();
        });

        // wait until the stop request is effectively visible, so the assertions below cannot pass
        // vacuously because the stopper thread was not scheduled yet
        while (!guard.isStopRequested()) {
            Thread.onSpinWait();
        }

        // Then: stop is blocked while the startup is in flight
        assertThat(stopReturned.await(200, TimeUnit.MILLISECONDS)).isFalse();

        // When: the startup completes
        boolean started = guard.endStart();

        // Then: stop unblocks, and the service must not transition to RUNNING
        assertThat(stopReturned.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(started).isFalse();
        stopper.join(1000);
    }

    @Test
    void shouldNotReportStartedWhenStartupCompletesAfterStopTimedOut() {
        // Given: a startup that outlives the stop wait
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();
        guard.beginStop(Duration.ofMillis(50));

        // When: the hung startup finally completes
        boolean started = guard.endStart();

        // Then
        assertThat(started).isFalse();
    }

    @Test
    void shouldProceedBestEffortWhenStartupHangs() {
        // Given: a startup that never completes
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();

        // When
        long before = System.nanoTime();
        guard.beginStop(Duration.ofMillis(100));
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        // Then: stop proceeded after the timeout instead of hanging
        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
        assertThat(elapsedMs).isLessThan(5_000);
    }

    @Test
    void shouldReportEffectiveStartOnlyOnFirstEndStart() {
        // Given
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();

        // When
        boolean first = guard.endStart();
        boolean second = guard.endStart();

        // Then: only the first call reports an effective start
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void shouldReportStopRequestedWhileWaitingOnStartup() throws InterruptedException {
        // Given: a startup in flight that checks the cooperative flag
        var guard = new ServiceLifecycleGuard();
        guard.beginStart();

        var observedWhileStarting = new AtomicBoolean(false);
        var stopRequestedSeen = new CountDownLatch(1);
        Thread starter = Thread.ofVirtual().start(() ->
        {
            try {
                // simulate a long startup polling the cooperative checkpoint
                while (!guard.isStopRequested()) {
                    Thread.onSpinWait();
                }
                observedWhileStarting.set(true);
                stopRequestedSeen.countDown();
            } finally {
                guard.endStart();
            }
        });

        // When
        Thread stopper = Thread.ofVirtual().start(() -> guard.beginStop(Duration.ofSeconds(10)));

        // Then
        assertThat(stopRequestedSeen.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(observedWhileStarting).isTrue();
        starter.join(1000);
        stopper.join(1000);
    }
}
