package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ExecutionEvent;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: the harness's {@link RecordingQueueFactory} is gated, so it must not leak into other test
 * contexts in this module (it would otherwise replace the real queues and break the runner tests).
 * This context does not set the gating property, so the ExecutionEvent queue here must be the real
 * bean, not a recording fake.
 */
@MicronautTest
class QueueFactoryLeakProbeTest {
    @Inject
    DispatchQueueInterface<ExecutionEvent> executionEventQueue;

    @Test
    void executionEventQueueShouldBeTheRealBeanNotAFake() {
        assertThat(executionEventQueue)
            .as("if this is a RecordingDispatchQueue, the gated recording-queue factory leaked into this context")
            .isNotInstanceOf(RecordingDispatchQueue.class);
    }
}
