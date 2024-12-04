package io.kestra.core.models.flows.sla;

import java.time.Instant;
import java.util.function.Consumer;

public interface SLAMonitorStorage {
    void save(SLAMonitor slaMonitor);

    void purge(String executionId);

    void processExpired(Instant now, Consumer<SLAMonitor> consumer);
}
