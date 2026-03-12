package io.kestra.queue;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractSubscriberTest {

    private QueueService queueService;
    private MetricRegistry metricRegistry;
    private IgnoreExecutionService ignoreExecutionService;

    @BeforeEach
    void setUp() {
        queueService = mock(QueueService.class);
        metricRegistry = mock(MetricRegistry.class);
        ignoreExecutionService = mock(IgnoreExecutionService.class);
        when(metricRegistry.timer(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(mock(io.micrometer.core.instrument.Timer.class));
    }

    private TestSubscriber createSubscriber() {
        return new TestSubscriber(queueService, metricRegistry, ignoreExecutionService);
    }

    @Test
    void shouldStartNotRunningAndNotPaused() {
        // Given
        var subscriber = createSubscriber();

        // Then
        assertThat(subscriber.isActive()).isFalse();
        assertThat(subscriber.isPaused()).isFalse();
    }

    @Test
    void shouldSetRunningOnMarkReady() {
        // Given
        var subscriber = createSubscriber();

        // When
        subscriber.markReady();

        // Then
        assertThat(subscriber.isActive()).isTrue();
    }

    @Test
    void markReadyShouldBeIdempotent() {
        // Given
        var subscriber = createSubscriber();

        // When
        subscriber.markReady();
        subscriber.markReady();

        // Then
        assertThat(subscriber.isActive()).isTrue();
    }

    @Test
    void shouldClearRunningOnMarkEnd() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When
        subscriber.markEnd();

        // Then
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void markEndShouldBeIdempotent() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When/Then
        assertThatNoException().isThrownBy(() -> {
            subscriber.markEnd();
            subscriber.markEnd();
        });
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void markEndShouldBeCallableWhenNeverStarted() {
        // Given
        var subscriber = createSubscriber();

        // When/Then
        assertThatNoException().isThrownBy(subscriber::markEnd);
    }

    @Test
    void shouldSetPausedOnPause() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When
        subscriber.pause();

        // Then
        assertThat(subscriber.isPaused()).isTrue();
    }

    @Test
    void shouldClearPausedOnResume() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.pause();

        // When
        subscriber.resume();

        // Then
        assertThat(subscriber.isPaused()).isFalse();
    }

    @Test
    void pauseShouldBeIdempotent() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When
        subscriber.pause();
        subscriber.pause();

        // Then
        assertThat(subscriber.isPaused()).isTrue();
    }

    @Test
    void resumeShouldBeIdempotentWhenNotPaused() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When/Then
        assertThatNoException().isThrownBy(subscriber::resume);
        assertThat(subscriber.isPaused()).isFalse();
    }

    @Test
    void shouldAllowPauseBeforeMarkReady() {
        // Given
        var subscriber = createSubscriber();

        // When
        assertThatNoException().isThrownBy(subscriber::pause);

        // Then
        assertThat(subscriber.isPaused()).isTrue();
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void shouldAllowPauseAfterClose() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.markEnd();
        subscriber.close();

        // When/Then
        assertThatNoException().isThrownBy(subscriber::pause);
    }

    @Test
    void shouldAllowResumeAfterClose() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.markEnd();
        subscriber.close();

        // When/Then
        assertThatNoException().isThrownBy(subscriber::resume);
    }

    @Test
    void waitIfPausedShouldReturnImmediatelyWhenNotPaused() throws InterruptedException {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        // When/Then - should not block
        subscriber.waitIfPaused();
    }

    @Test
    void waitIfPausedShouldBlockUntilResumed() throws InterruptedException {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.pause();

        var waitStarted = new CountDownLatch(1);
        var waitCompleted = new CountDownLatch(1);

        // When
        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                waitStarted.countDown();
                subscriber.waitIfPaused();
                waitCompleted.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Then - should be blocked
        assertThat(waitStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(waitCompleted.await(100, TimeUnit.MILLISECONDS)).isFalse();

        // When - resume
        subscriber.resume();

        // Then - should unblock
        assertThat(waitCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        waiter.join(1000);
    }

    @Test
    void waitIfPausedShouldUnblockWhenClosed() throws InterruptedException {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.pause();

        var waitCompleted = new CountDownLatch(1);

        // Simulate a real subscriber loop: waitIfPaused → markEnd on exit
        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                subscriber.waitIfPaused();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscriber.markEnd();
                waitCompleted.countDown();
            }
        });

        Thread.sleep(50);

        // When
        subscriber.close();

        // Then
        assertThat(waitCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        waiter.join(1000);
    }

    @Test
    void closeShouldBeIdempotent() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.markEnd();

        // When/Then
        assertThatNoException().isThrownBy(() -> {
            subscriber.close();
            subscriber.close();
        });
    }

    @Test
    void closeShouldBeNoOpWhenNeverStarted() {
        // Given
        var subscriber = createSubscriber();

        // When/Then
        assertThatNoException().isThrownBy(subscriber::close);
    }

    @Test
    void closeShouldSetRunningToFalse() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.markEnd();

        // When
        subscriber.close();

        // Then
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void closeShouldResumeAndUnblockLoop() throws InterruptedException {
        // Given - subscriber is running and paused with a simulated loop
        var subscriber = createSubscriber();
        subscriber.markReady();
        subscriber.pause();

        var closeCompleted = new CountDownLatch(1);

        Thread loop = Thread.ofVirtual().start(() -> {
            try {
                subscriber.waitIfPaused();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscriber.markEnd();
            }
        });

        Thread.sleep(50);

        // When
        Thread closer = Thread.ofVirtual().start(() -> {
            subscriber.close();
            closeCompleted.countDown();
        });

        // Then
        assertThat(closeCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        loop.join(1000);
        closer.join(1000);
    }

    @Test
    void shouldSupportPauseBeforeSubscribePattern() throws InterruptedException {
        // Given - this is the pattern used in WorkerJobDispatcher:
        // pause() → subscribe() → resume() when ready
        var subscriber = createSubscriber();
        subscriber.pause();

        var loopEntered = new CountDownLatch(1);
        var messageProcessed = new AtomicBoolean(false);
        var loopExited = new CountDownLatch(1);

        // When - start the subscriber loop (simulating subscribe)
        Thread loop = Thread.ofVirtual().start(() -> {
            subscriber.markReady();
            try {
                while (subscriber.isActive()) {
                    loopEntered.countDown();
                    subscriber.waitIfPaused();
                    if (!subscriber.isActive()) break;
                    messageProcessed.set(true);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscriber.markEnd();
                loopExited.countDown();
            }
        });

        // Then - loop should be blocked on waitIfPaused
        assertThat(loopEntered.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(messageProcessed.get()).isFalse();

        // When - resume
        subscriber.resume();

        // Then - loop processes and exits
        assertThat(loopExited.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(messageProcessed.get()).isTrue();
        loop.join(1000);
    }

    @Test
    void shouldNotThrowOnConcurrentPauseAndClose() throws InterruptedException {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();

        var loopExited = new CountDownLatch(1);
        Thread loop = Thread.ofVirtual().start(() -> {
            try {
                while (subscriber.isActive()) {
                    subscriber.waitIfPaused();
                    if (!subscriber.isActive()) break;
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscriber.markEnd();
                loopExited.countDown();
            }
        });

        // When - pause and close concurrently
        var errors = new AtomicReference<Throwable>();

        Thread pauser = Thread.ofVirtual().start(() -> {
            try {
                subscriber.pause();
            } catch (Throwable t) {
                errors.set(t);
            }
        });

        Thread closer = Thread.ofVirtual().start(() -> {
            try {
                subscriber.close();
            } catch (Throwable t) {
                errors.compareAndSet(null, t);
            }
        });

        // Then
        pauser.join(1000);
        closer.join(2000);
        assertThat(loopExited.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).isNull();
        loop.join(1000);
    }

    @Test
    void shouldCallShutdownOnMarkEndWithException() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        var noOpContext = new NoOpShutdownContext(new AtomicBoolean(false));
        KestraContext.setContext(noOpContext);

        // When
        subscriber.markEnd(new RuntimeException("test error"));

        // Then
        assertThat(subscriber.isActive()).isFalse();
        assertThat(noOpContext.isShutdownCalled()).isTrue();
    }

    @Test
    void shouldSetActiveToFalseOnMarkEndWithException() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        KestraContext.setContext(new NoOpShutdownContext(new AtomicBoolean(false)));

        // When
        subscriber.markEnd(new RuntimeException("test error"));

        // Then
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void shouldBeIdempotentOnMarkEndWithExceptionCalledTwice() {
        // Given
        var subscriber = createSubscriber();
        subscriber.markReady();
        var noOpContext = new NoOpShutdownContext(new AtomicBoolean(false));
        KestraContext.setContext(noOpContext);

        // When
        subscriber.markEnd(new RuntimeException("first"));
        subscriber.markEnd(new RuntimeException("second"));

        // Then
        assertThat(subscriber.isActive()).isFalse();
        // shutdown() is called twice because markEnd(Throwable) always calls it,
        // but KestraContext.Initializer.shutdown() itself is idempotent via AtomicBoolean
        assertThat(noOpContext.isShutdownCalled()).isTrue();
    }

    /**
     * Minimal concrete implementation for testing AbstractSubscriber.
     */
    static class TestSubscriber extends AbstractSubscriber<TestEvent> {

        TestSubscriber(QueueService queueService, MetricRegistry metricRegistry, IgnoreExecutionService ignoreExecutionService) {
            super(TestEvent.class, "test-queue", queueService, metricRegistry, ignoreExecutionService);
        }

        @Override
        public QueueSubscriber<TestEvent> subscribe(Consumer<Either<TestEvent, DeserializationException>> consumer) {
            return this;
        }
    }

    record TestEvent(String key) implements Event {
    }
}