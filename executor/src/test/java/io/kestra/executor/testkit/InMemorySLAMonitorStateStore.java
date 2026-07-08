package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.executor.SLAMonitorStateStore;

/**
 * List-backed {@link SLAMonitorStateStore}.
 */
public class InMemorySLAMonitorStateStore implements SLAMonitorStateStore {
    private final List<SLAMonitor> monitors = new CopyOnWriteArrayList<>();

    @Override
    public void save(SLAMonitor slaMonitor) {
        monitors.add(slaMonitor);
    }

    @Override
    public void purge(String executionId) {
        monitors.removeIf(monitor -> executionId.equals(monitor.getExecutionId()));
    }

    @Override
    public void processExpired(Instant now, Consumer<SLAMonitor> consumer) {
        List<SLAMonitor> expired = monitors.stream()
            .filter(monitor -> !monitor.getDeadline().isAfter(now))
            .toList();
        expired.forEach(monitor ->
        {
            consumer.accept(monitor);
            monitors.remove(monitor);
        });
    }

    /**
     * Every pending monitor.
     */
    public List<SLAMonitor> pending() {
        return List.copyOf(monitors);
    }
}
