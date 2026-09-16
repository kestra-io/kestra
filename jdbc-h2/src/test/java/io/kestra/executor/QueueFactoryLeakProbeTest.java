package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ExecutionEvent;

import jakarta.inject.Inject;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Probe: does the nested {@code @Factory @Replaces} declared in {@link ExecutorStateMachineH2PocTest}
 * leak into other test contexts in this module? This context does not set the gating property, so
 * with the factory correctly gated the ExecutionEvent queue here is the real bean, not a Mockito mock.
 */
@MicronautTest
class QueueFactoryLeakProbeTest {
    @Inject
    DispatchQueueInterface<ExecutionEvent> executionEventQueue;

    @Test
    void executionEventQueueShouldBeTheRealBeanNotAMock() {
        assertThat(Mockito.mockingDetails(executionEventQueue).isMock())
            .as("if true, the state-machine test's @Factory leaked into this context")
            .isFalse();
    }
}
