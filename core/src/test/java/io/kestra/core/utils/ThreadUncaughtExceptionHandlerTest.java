package io.kestra.core.utils;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.storages.StorageInterface;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadUncaughtExceptionHandlerTest {

    private KestraContext previousContext;

    @BeforeEach
    void saveKestraContext() {
        try {
            previousContext = KestraContext.getContext();
        } catch (IllegalStateException e) {
            previousContext = null;
        }
    }

    @AfterEach
    void restoreKestraContext() {
        // do not leak the recording stub into later tests of the same fork
        KestraContext.setContext(previousContext);
    }

    @Test
    void shouldShutdownTheBoundContextNotTheStaticOne() {
        // Given: the JVM-global static points to another (newer) context
        var boundContext = new RecordingContext();
        var otherContext = new RecordingContext();
        KestraContext.setContext(otherContext);

        var handler = new ThreadUncaughtExceptionHandler(boundContext);

        // When
        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        // Then
        assertThat(boundContext.shutdownCalled).isTrue();
        assertThat(otherContext.shutdownCalled).isFalse();
    }

    @Test
    void shouldCaptureTheContextCurrentAtConstructionTime() {
        // Given: a handler created while the static points to the owning context
        var owningContext = new RecordingContext();
        KestraContext.setContext(owningContext);
        var handler = new ThreadUncaughtExceptionHandler();

        // When: a newer context replaced the static before the failure happens
        var newerContext = new RecordingContext();
        KestraContext.setContext(newerContext);
        handler.uncaughtException(Thread.currentThread(), new RuntimeException("boom"));

        // Then
        assertThat(owningContext.shutdownCalled).isTrue();
        assertThat(newerContext.shutdownCalled).isFalse();
    }

    /**
     * A recording {@link KestraContext} whose environments contain "test" so the handler never
     * calls {@code Runtime.getRuntime().exit(1)}.
     */
    private static final class RecordingContext extends KestraContext {
        private volatile boolean shutdownCalled = false;

        @Override
        public void shutdown() {
            this.shutdownCalled = true;
        }

        @Override
        public Set<String> getEnvironments() {
            return Set.of("test");
        }

        @Override
        public ServerType getServerType() {
            return ServerType.STANDALONE;
        }

        @Override
        public int getAllocatedCpuCores() {
            return 1;
        }

        @Override
        public Optional<Integer> getWorkerMaxNumThreads() {
            return Optional.empty();
        }

        @Override
        public void injectWorkerConfigs(Integer maxNumThreads) {
            // no-op
        }

        @Override
        public String getVersion() {
            return null;
        }

        @Override
        public PluginRegistry getPluginRegistry() {
            return null;
        }

        @Override
        public StorageInterface getStorageInterface() {
            return null;
        }
    }
}
