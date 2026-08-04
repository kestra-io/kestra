package io.kestra.core.junit.extensions;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import io.kestra.core.junit.services.ContextShutdownRecorder;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards every fixture entry point against the stopped-context regression: each must abort instead of
 * resolving beans from an already-stopped context (see {@link ContextShutdownRecorder} for why that
 * produces a misleading failure).
 */
class ExtensionContextShutdownGuardTest {

    /** Exposes the {@code protected} context field so a stopped context can be injected directly. */
    private static final class StoppedContextLoader extends AbstractLoaderExtension {
        private StoppedContextLoader(ApplicationContext context) {
            this.context = context;
        }
    }

    private static ApplicationContext stoppedContext() {
        ApplicationContext stopped = mock(ApplicationContext.class);
        when(stopped.isRunning()).thenReturn(false);
        return stopped;
    }

    @Test
    void shouldAbortWithoutResolvingBeansWhenContextStoppedOnCreateTenant() {
        // Given
        ApplicationContext stopped = stoppedContext();
        StoppedContextLoader loader = new StoppedContextLoader(stopped);

        // When / Then
        assertThatThrownBy(() -> loader.createTenant(null, "some-tenant"))
            .isInstanceOf(TestAbortedException.class)
            .hasMessageContaining("Application context is no longer running");

        // The point of the guard: no bean is resolved, so no unrelated failure can mask the shutdown.
        verify(stopped, never()).getBean(any(Class.class));
    }

    @Test
    void shouldAbortWithoutResolvingBeansWhenContextStoppedOnLoadFlows() {
        // Given
        ApplicationContext stopped = stoppedContext();
        StoppedContextLoader loader = new StoppedContextLoader(stopped);

        // When / Then
        assertThatThrownBy(() -> loader.loadFlows(null, "some-tenant", new String[] { "flows/loaddir" }))
            .isInstanceOf(TestAbortedException.class)
            .hasMessageContaining("Application context is no longer running");

        verify(stopped, never()).getBean(any(Class.class));
    }

    @Test
    void shouldAbortWithoutResolvingBeansWhenContextStoppedOnEvaluateTrigger() {
        // Given — the package-private context field stands in for a completed lookup
        ApplicationContext stopped = stoppedContext();
        TriggerEvaluationExtension extension = new TriggerEvaluationExtension();
        extension.context = stopped;

        // When / Then
        assertThatThrownBy(() -> extension.resolveParameter(null, null))
            .isInstanceOf(TestAbortedException.class)
            .hasMessageContaining("Application context is no longer running");

        verify(stopped, never()).getBean(any(Class.class));
    }

    @Test
    void shouldAbortOnEvaluateTriggerWhenContextStopsBetweenTwoResolutions() {
        // Given a context that is running for the first resolution and stopped for the next, as happens
        // across the invocations of a @TestTemplate sharing one extension instance
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.isRunning()).thenReturn(true, false);
        when(context.getBean(any(Class.class))).thenThrow(new IllegalStateException("first resolution is not under test"));
        TriggerEvaluationExtension extension = new TriggerEvaluationExtension();
        extension.context = context;

        // When the first resolution gets past the guard and fails later on
        assertThatThrownBy(() -> extension.resolveParameter(null, null))
            .isNotInstanceOf(TestAbortedException.class);

        // Then the second one is aborted rather than reusing the stopped context
        assertThatThrownBy(() -> extension.resolveParameter(null, null))
            .isInstanceOf(TestAbortedException.class)
            .hasMessageContaining("Application context is no longer running");
    }
}
