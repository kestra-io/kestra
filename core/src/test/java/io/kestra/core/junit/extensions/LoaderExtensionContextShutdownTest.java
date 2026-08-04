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
 * Guards {@link AbstractLoaderExtension} against the stopped-context regression: a fixture must abort
 * instead of resolving beans against an already-stopped context (see {@link ContextShutdownRecorder} for
 * why that produces a misleading failure).
 */
class LoaderExtensionContextShutdownTest {

    /** Exposes the {@code protected} context field so a stopped context can be injected directly. */
    private static final class StoppedContextLoader extends AbstractLoaderExtension {
        private StoppedContextLoader(ApplicationContext context) {
            this.context = context;
        }
    }

    @Test
    void shouldAbortWithoutResolvingBeansWhenContextStoppedOnCreateTenant() {
        // Given
        ApplicationContext stopped = mock(ApplicationContext.class);
        when(stopped.isRunning()).thenReturn(false);
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
        ApplicationContext stopped = mock(ApplicationContext.class);
        when(stopped.isRunning()).thenReturn(false);
        StoppedContextLoader loader = new StoppedContextLoader(stopped);

        // When / Then
        assertThatThrownBy(() -> loader.loadFlows(null, "some-tenant", new String[] { "flows/loaddir" }))
            .isInstanceOf(TestAbortedException.class)
            .hasMessageContaining("Application context is no longer running");

        verify(stopped, never()).getBean(any(Class.class));
    }
}
