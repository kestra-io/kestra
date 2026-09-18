package io.kestra.executor.testkit;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs every task on the calling thread, so the executor's per-execution batching code executes
 * unchanged but sequentially.
 */
public final class DirectExecutorService extends AbstractExecutorService {
    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public void shutdown() {
        // nothing running
    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
