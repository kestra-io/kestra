package io.kestra.executor.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Stands in for the executor's loop scheduler: {@code scheduleAtFixedRate} registers the loop,
 * nothing runs until a test calls {@link #tick(int)}. Loops are indexed in registration order —
 * for {@code DefaultExecutor}: 0 = execution delays, 1 = SLA monitors, 2 = multiple-condition purge.
 */
public final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
    private final List<Runnable> loops = new ArrayList<>();

    public void tick(int loop) {
        loops.get(loop).run();
    }

    public int loopCount() {
        return loops.size();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        loops.add(command);
        return new Registered<>();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("one-shot schedules are not used by the executor");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("one-shot schedules are not used by the executor");
    }

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

    private static final class Registered<V> extends CompletableFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }
    }
}
