package io.kestra.core.junit.services;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Covers the shutdown diagnostic consumed by
 * {@link io.kestra.core.junit.extensions.AbstractLoaderExtension} when it finds a stopped context.
 */
class ContextShutdownRecorderTest {

    @Test
    void shouldReportThreadAndCallerFramesWithoutPlumbingWhenDescribingAShutdown() {
        // Given a shutdown observed from a background thread, as when a queue consumer triggers it
        Thread thread = new Thread(() -> { }, "queue-memory-probe");

        // When
        String description = ContextShutdownRecorder.describe(thread, Thread.currentThread().getStackTrace());

        // Then the thread and the frames that asked for the shutdown are reported, the plumbing is not
        assertThat(description)
            .contains("queue-memory-probe")
            .contains(ContextShutdownRecorderTest.class.getName())
            .doesNotContain(Thread.class.getName() + ".getStackTrace");
    }

    @Test
    void shouldAttributeShutdownToItsOwnContextWhenContextIsClosed() {
        // Given a real context that records its own shutdown
        ApplicationContext ownContext = ApplicationContext.run();

        // When
        ownContext.close();

        // Then the recording is attributed to that context rather than flagged as unrelated, and the
        // recorder's own frame is dropped
        assertThat(ContextShutdownRecorder.describeLastShutdown(ownContext))
            .contains("Context was shut down by thread")
            .doesNotContain("may be unrelated")
            .doesNotContain(ContextShutdownRecorder.class.getName() + ".onApplicationEvent");

        // And a context the recording is not about is told so
        assertThat(ContextShutdownRecorder.describeLastShutdown(mock(ApplicationContext.class)))
            .contains("may be unrelated");
    }
}
