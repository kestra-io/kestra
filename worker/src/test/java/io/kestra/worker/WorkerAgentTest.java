package io.kestra.worker;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.services.MaintenanceService;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.services.WorkerConnectionService;

import io.micronaut.context.event.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkerAgentTest {

    @Test
    void shouldUseConfiguredJobBufferSizeWhenSet() {
        assertThat(workerAgent(2).jobBufferSize(8)).isEqualTo(2);
    }

    @Test
    void shouldCapJobBufferSizeToThreadCountWhenConfiguredAbove() {
        assertThat(workerAgent(16).jobBufferSize(8)).isEqualTo(8);
    }

    @Test
    void shouldDefaultJobBufferSizeToThreadCountWhenNotSet() {
        assertThat(workerAgent(null).jobBufferSize(8)).isEqualTo(8);
    }

    @SuppressWarnings("unchecked")
    private static WorkerAgent workerAgent(Integer jobBufferSize) {
        return new WorkerAgent(
            mock(ApplicationEventPublisher.class),
            mock(WorkerConnectionService.class),
            mock(WorkerJobExecutor.class),
            mock(WorkerJobFetcher.class),
            List.of(),
            mock(MaintenanceService.class),
            mock(MetricRegistry.class),
            mock(ServerConfig.class),
            new WorkerConfig(null, jobBufferSize)
        );
    }
}
