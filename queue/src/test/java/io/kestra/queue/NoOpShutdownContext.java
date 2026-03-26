package io.kestra.queue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.storages.StorageInterface;

/**
 * A {@link KestraContext} that intercepts {@link #shutdown()} to prevent actual ApplicationContext closure
 * during tests, while recording that shutdown was requested.
 * <p>
 * When a delegate is provided, all methods except {@code shutdown()} are forwarded to it.
 * When no delegate is provided (unit tests without a Micronaut context), methods return safe defaults.
 */
public class NoOpShutdownContext extends KestraContext {
    private final KestraContext delegate;
    private final AtomicBoolean shutdownCalled;

    /**
     * Creates a context that delegates to an existing context.
     * Use this in integration tests where a real {@link KestraContext} is available.
     */
    public NoOpShutdownContext(KestraContext delegate, AtomicBoolean shutdownCalled) {
        this.delegate = delegate;
        this.shutdownCalled = shutdownCalled;
    }

    /**
     * Creates a standalone context with no delegate.
     * Use this in unit tests where no Micronaut context is available.
     */
    public NoOpShutdownContext(AtomicBoolean shutdownCalled) {
        this(null, shutdownCalled);
    }

    public boolean isShutdownCalled() {
        return shutdownCalled.get();
    }

    @Override
    public void shutdown() {
        shutdownCalled.set(true);
    }

    @Override
    public ServerType getServerType() {
        return delegate != null ? delegate.getServerType() : null;
    }

    @Override
    public int getAllocatedCpuCores() {
        return delegate != null ? delegate.getAllocatedCpuCores() : Runtime.getRuntime().availableProcessors();
    }

    @Override
    public Optional<Integer> getWorkerMaxNumThreads() {
        return delegate != null ? delegate.getWorkerMaxNumThreads() : Optional.empty();
    }

    @Override
    public Optional<String> getWorkerGroupKey() {
        return delegate != null ? delegate.getWorkerGroupKey() : Optional.empty();
    }

    @Override
    public void injectWorkerConfigs(Integer maxNumThreads, String workerGroupKey) {
        if (delegate != null)
            delegate.injectWorkerConfigs(maxNumThreads, workerGroupKey);
    }

    @Override
    public String getVersion() {
        return delegate != null ? delegate.getVersion() : "test";
    }

    @Override
    public PluginRegistry getPluginRegistry() {
        return delegate != null ? delegate.getPluginRegistry() : null;
    }

    @Override
    public StorageInterface getStorageInterface() {
        return delegate != null ? delegate.getStorageInterface() : null;
    }

    @Override
    public Set<String> getEnvironments() {
        return delegate != null ? delegate.getEnvironments() : Set.of();
    }
}
