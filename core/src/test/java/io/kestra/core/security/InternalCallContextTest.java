package io.kestra.core.security;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalCallContextTest {

    @Test
    void shouldMarkTheCallingThreadForTheDurationOfTheAction() {
        assertThat(InternalCallContext.isInternalCall()).isFalse();

        InternalCallContext.runAsInternalCall(() -> assertThat(InternalCallContext.isInternalCall()).isTrue());

        assertThat(InternalCallContext.isInternalCall()).isFalse();
    }

    @Test
    void shouldKeepTheMarkerUntilTheOutermostActionReturnsWhenNested() {
        InternalCallContext.runAsInternalCall(() ->
        {
            InternalCallContext.runAsInternalCall(() -> assertThat(InternalCallContext.isInternalCall()).isTrue());

            assertThat(InternalCallContext.isInternalCall()).isTrue();
        });

        assertThat(InternalCallContext.isInternalCall()).isFalse();
    }

    @Test
    void shouldNotMarkOtherThreads() throws InterruptedException {
        AtomicBoolean internalOnOtherThread = new AtomicBoolean(true);
        Thread other = new Thread(() -> internalOnOtherThread.set(InternalCallContext.isInternalCall()));

        InternalCallContext.runAsInternalCall(other::start);
        other.join();

        assertThat(internalOnOtherThread).isFalse();
    }
}
